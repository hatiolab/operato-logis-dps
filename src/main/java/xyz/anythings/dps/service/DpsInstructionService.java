package xyz.anythings.dps.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.Rack;
import xyz.anythings.base.event.EventConstants;
import xyz.anythings.base.event.main.BatchInstructEvent;
import xyz.anythings.base.service.api.IInstructionService;
import xyz.anythings.dps.service.util.DpsBatchJobConfigUtil;
import xyz.anythings.sys.event.model.EventResultSet;
import xyz.anythings.sys.service.AbstractExecutionService;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.sys.SysConstants;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.util.ValueUtil;

/**
 * DPS 용 작업 지시 서비스
 * 
 * @author yang
 */
@Component("dpsInstructionService")
public class DpsInstructionService extends AbstractExecutionService implements IInstructionService {

	@Override
	public Map<String, Object> searchInstructionData(JobBatch batch, Object... params) {
		// DPS에서는 구현이 필요없음
		return null;
	}	
	
	@SuppressWarnings("unchecked")
	@Override
	public int instructBatch(JobBatch batch, List<String> equipIdList, Object... params) {
		// 1. 배치 정보에 설정값이 없는지 체크
		this.checkJobAndIndConfigSet(batch);
		
		// 2. 작업 배치 정보로 설비 리스트 조회
		List<?> equipList = this.searchEquipListByBatch(batch, equipIdList);
		
		// 3. 랙에 배치 할당 
		List<Rack> rackList = (List<Rack>)equipList;
		for(Rack rack : rackList) {
			rack.setBatchId(batch.getId());
		}
		
		AnyOrmUtil.updateBatch(rackList, 100, "batchId", "updatedAt");
		
		// 4. 소분류 코드, 방면 분류 코드 값을 설정에 따라서 주문 정보에 추가한다.
		this.doUpdateClassificationCodes(batch, params);

		// 5. 대상 분류 
		this.doClassifyOrders(batch, equipList, params);
		
		// 6. 추천 로케이션 정보 생성
		this.doRecommendCells(batch, equipList, params);
		
		// 7. 작업 지시 처리
		int retCnt = this.doInstructBatch(batch, equipList, params);
		
		// 8. 작업 지시 후 박스 요청 
		this.doRequestBox(batch, equipList, params);
		
		// 9. 건수 리턴
		return retCnt;
	}

	@Override
	public int instructTotalpicking(JobBatch batch, List<String> equipIdList, Object... params) {
		// 1. 토털 피킹 전 처리 이벤트 
		EventResultSet befResult = this.publishTotalPickingEvent(EventConstants.EVENT_STEP_BEFORE, batch, equipIdList, params);
		
		// 2. 다음 처리 취소일 경우 결과 리턴 
		if(befResult.isAfterEventCancel()) {
			return ValueUtil.toInteger(befResult.getResult());
		}
		
		// 3. 토털 피킹 후 처리 이벤트
		EventResultSet aftResult = this.publishTotalPickingEvent(EventConstants.EVENT_STEP_AFTER, batch, equipIdList, params);
		
		// 4. 후처리 이벤트 실행 후 리턴 결과가 있으면 해당 결과 리턴 
		if(aftResult.isExecuted()) {
			if(aftResult.getResult() != null ) { 
				return ValueUtil.toInteger(aftResult.getResult());
			}
		}
		
		return 0;
	}

	@Override
	public int mergeBatch(JobBatch mainBatch, JobBatch newBatch, Object... params) {
		// 1. 작업 배치 정보로 설비 리스트 조회
		List<?> equipList = this.searchEquipListByBatch(mainBatch, null);
		
		// 2. 소분류 코드, 방면 분류 코드 값을 설정에 따라서 주문 정보에 추가한다.
		this.doUpdateClassificationCodes(newBatch, params);

		// 3. 대상 분류 
		this.doClassifyOrders(newBatch, equipList, params);
		
		// 4. 추천 로케이션 정보 생성
		this.doRecommendCells(newBatch, equipList, params);
		
		// 5. 작업 병합 처리
		int retCnt = this.doMergeBatch(mainBatch, newBatch, equipList, params);
		
		// 6. 작업 병합 후 박스 요청 
		this.doRequestBox(mainBatch, equipList, params);
		
		// 7. 병합 건수 리턴
		return retCnt;
	}

	@Override
	public int cancelInstructionBatch(JobBatch batch) {
		// TODO - 토털 피킹 I/F가 완료되었을 경우 어떻게 처리할 것인지 확실치 않으므로 일단 지원하지 않음
		throw ThrowUtil.newNotSupportedMethodYet();
	}
	
	/**
	 * 배치에 설정값이 설정되어 있는지 체크하고 기본 설정값으로 설정한다.
	 * 
	 * @param batch
	 */
	private void checkJobAndIndConfigSet(JobBatch batch) {		
		// 1. 작업 관련 설정이 없는 경우 기본 작업 설정을 찾아서 세팅
		if(ValueUtil.isEmpty(batch.getJobConfigSetId())) {			
			throw ThrowUtil.newJobConfigNotSet();
		}
				
		// 2. 표시기 관련 설정이 없는 경우 표시기 설정을 찾아서 세팅
		if(ValueUtil.isEmpty(batch.getIndConfigSetId())) {
			throw ThrowUtil.newIndConfigNotSet();
		}		
	}
	
	/**
	 * 배치 데이터에 대해 설비 정보 여부 를 찾아 대상 설비 리스트를 리턴
	 * 
	 * @param batch
	 * @param equipIdList
	 * @return
	 */
	private List<?> searchEquipListByBatch(JobBatch batch, List<String> equipIdList) {
		Class<?> equipClazz = null;
		
		// 1. 설비 타입에 대한 마스터 엔티티 구분
		if(ValueUtil.isEqual(batch.getEquipType(), LogisConstants.EQUIP_TYPE_RACK)) {
			equipClazz = Rack.class;
		} else {
			// TODO : 소터 등등등 추가 
			return null;
		}
		
		// 2. 작업 대상 설비가 있으면 그대로 return 
		if(ValueUtil.isNotEmpty(equipIdList)) {
			return this.searchEquipByIds(batch.getDomainId(), equipClazz, equipIdList);
		}
		
		// 3. batch 에 작업 대상 설비 타입만 지정되어 있으면 
		return this.searchEquipByBatch(equipClazz, batch);
	}
	
	/**
	 * 설비 ID 리스트로 설비 마스터 조회
	 * 
	 * @param domainId
	 * @param clazz
	 * @param equipIdList
	 * @return
	 */
	private <T> List<T> searchEquipByIds(long domainId, Class<T> clazz, List<String> equipIdList) {
		Query condition = AnyOrmUtil.newConditionForExecution(domainId);
		condition.addFilter(LogisConstants.ENTITY_FIELD_ID, SysConstants.IN, equipIdList);
		return this.queryManager.selectList(clazz,condition);
	}
	
	/**
	 * 배치 정보로 설비 마스터 리스트 조회 
	 * 
	 * @param clazz
	 * @param batch
	 * @return
	 */
	private <T> List<T> searchEquipByBatch(Class<T> clazz, JobBatch batch) {
		return AnyEntityUtil.searchEntitiesBy(batch.getDomainId(), false, clazz, null
				, "areaCd,stageCd,activeFlag,jobType,batchId"
				, batch.getAreaCd(), batch.getStageCd(), Boolean.TRUE, batch.getJobType(), batch.getId());	
	}
		
	/**
	 * 작업 배치 소속 주문 데이터의 소분류, 방면 분류 코드를 업데이트 ...
	 * 
	 * @param batch
	 * @param params
	 */
	private void doUpdateClassificationCodes(JobBatch batch, Object ... params) {
		// 1. 소분류 매핑 필드 - class_cd 매핑 
		String class1TargetField = DpsBatchJobConfigUtil.getBoxMappingTargetField(batch);
		
		// 2. 방면분류 매핑 필드 - class2_cd 매핑
		String class2TargetField = DpsBatchJobConfigUtil.getMappingFieldForOutClassification(batch);
		
		// 3. 주문 정보 업데이트
		String sql = "UPDATE ORDERS SET CLASS_CD = :classCd, CLASS2_CD = :class2Cd WHERE DOMAIN_ID = :domainId AND BATCH_ID = :batchId";
		Map<String, Object> updateParams = ValueUtil.newMap("classCd,class2Cd", class1TargetField, class2TargetField);
		this.queryManager.executeBySql(sql, updateParams);
	}
	
	/**
	 * 작업 대상 분류
	 * 
	 * @param batch
	 * @param equipList
	 * @param params
	 */
	private void doClassifyOrders(JobBatch batch, List<?> equipList, Object... params) {
		// 1. 전처리 이벤트   
		EventResultSet befResult = this.publishClassificationEvent(EventConstants.EVENT_STEP_BEFORE, batch, equipList, params);
		
		// 2. 다음 처리 취소 일 경우 결과 리턴 
		if(!befResult.isAfterEventCancel()) {
			
			// 3. 대상 분류 프로세싱 
			this.processClassifyOrders(batch, equipList, params);
			
			// 4. 후처리 이벤트 
			this.publishClassificationEvent(EventConstants.EVENT_STEP_AFTER, batch, equipList, params);			
		}
	}
	
	/**
	 * 대상 분류 프로세싱 
	 * 
	 * @param batch
	 * @param equipList
	 * @param params
	 * @return
	 */
	private int processClassifyOrders(JobBatch batch, List<?> equipList, Object... params) {
		// 1. 단포 작업 활성화 여부 
		boolean useSinglePack = DpsBatchJobConfigUtil.isSingleSkuNpcsClassEnabled(batch);
		// 2. 파라미터 생성
		Map<String, Object> inputParams = ValueUtil.newMap("P_IN_DOMAIN_ID,P_IN_BATCH_ID,P_IN_SINGLE_PACK", batch.getDomainId(), batch.getId(),useSinglePack);
		// 3. 프로시져 콜 
		Map<?, ?> result = this.queryManager.callReturnProcedure("OP_DPS_BATCH_SET_ORDER_TYPE", inputParams, Map.class);
		// 4. 처리 건수 취합
		int resultCnt = ValueUtil.toInteger(result.get("P_OUT_MT_COUNT"));
		resultCnt += ValueUtil.toInteger(result.get("P_OUT_OT_COUNT"));
		// 5. 처리 건수 리턴 
		return resultCnt;
	}
	
	/**
	 * 추천 로케이션 처리
	 * 
	 * @param batch
	 * @param equipList
	 * @param params
	 */
	private void doRecommendCells(JobBatch batch, List<?> equipList, Object ... params) {
		// 1. 전 처리 이벤트
		EventResultSet befResult = this.publishRecommendCellsEvent(EventConstants.EVENT_STEP_BEFORE, batch, equipList, params);
		
		// 2. 다음 처리 취소 일 경우 결과 리턴 
		if(!befResult.isAfterEventCancel()) {
			
			// 3. 작업 지시 실행
			this.processRecommendCells(batch, equipList, params);
			
			// 4. 후 처리 이벤트 
			this.publishRecommendCellsEvent(EventConstants.EVENT_STEP_AFTER, batch, equipList, params);			
		}		
	}
	
	/**
	 * 추천 로케이션 실행
	 * 
	 * @param batch
	 * @param equipList
	 * @param params
	 */
	private void processRecommendCells(JobBatch batch, List<?> equipList, Object ... params) {
		// 재고 적치 추천 셀 사용 유무 
		boolean useRecommendCell = DpsBatchJobConfigUtil.isRecommendCellEnabled(batch);
		
		if(useRecommendCell) {
			// 1. 파라미터 생성
			Map<String, Object> inputParams = ValueUtil.newMap("P_IN_DOMAIN_ID,P_IN_BATCH_ID,P_IN_RECOMMEND_CELL", batch.getDomainId(), batch.getId(), useRecommendCell);
			// 2. 프로시져 콜 
			this.queryManager.callReturnProcedure("OP_DPS_RECOMMEND_CELLS", inputParams, Map.class);
		}		
	}
	
	/**
	 * 작업 지시 처리
	 * 
	 * @param batch
	 * @param equipList
	 * @param params
	 * @return
	 */
	private int doInstructBatch(JobBatch batch, List<?> equipList, Object ... params) {
		// 1. 전 처리 이벤트
		EventResultSet befResult = this.publishInstructionEvent(EventConstants.EVENT_STEP_BEFORE, batch, equipList, params);
		
		// 2. 다음 처리 취소 일 경우 결과 리턴 
		if(befResult.isAfterEventCancel()) {
			return ValueUtil.toInteger(befResult.getResult());
		}
		
		// 3. 작업 지시 실행
		int resultCnt = this.processInstruction(batch, params);
		
		// 4. 후 처리 이벤트 
		EventResultSet aftResult = this.publishInstructionEvent(EventConstants.EVENT_STEP_AFTER, batch, equipList, params);
		
		// 5. 후 처리 이벤트가 실행 되고 리턴 결과가 있으면 해당 결과 리턴 
		if(aftResult.isExecuted()) {
			if(aftResult.getResult() != null ) { 
				resultCnt += ValueUtil.toInteger(aftResult.getResult());
			}
		}

		return resultCnt;
	}
	
	/**
	 * 작업 지시 실행 
	 *  
	 * @param batch
	 * @param params
	 * @return
	 */
	private int processInstruction(JobBatch batch, Object ... params) {
		// 1. 단포 작업 활성화 여부 
		boolean useSinglePack = DpsBatchJobConfigUtil.isSingleSkuNpcsClassEnabled(batch);
		// 2. 재고 적치 추천 셀 사용 유무 
		boolean useRecommendCell = DpsBatchJobConfigUtil.isRecommendCellEnabled(batch);
		// 3. 호기별 배치 분리 여부
		boolean useSeparatedBatch = DpsBatchJobConfigUtil.isSeparatedBatchByRack(batch);

		// 4. 파라미터 생성
		Map<String, Object> inputParams = ValueUtil.newMap("P_IN_DOMAIN_ID,P_IN_BATCH_ID,P_IN_SINGLE_PACK,P_IN_RECOMMEND_CELL,P_IN_SEPARATE_BATCH"
				, batch.getDomainId(), batch.getId(), useSinglePack, useRecommendCell, useSeparatedBatch);
		// 5. 프로시져 콜
		this.queryManager.callReturnProcedure("OP_DPS_BATCH_INSTRUCT", inputParams, Map.class);
		
		return 1;
	}

	/**
	 * 박스 요청 처리
	 *  
	 * @param batch
	 * @param equipList
	 * @param params
	 * @return
	 */
	private int doRequestBox(JobBatch batch, List<?> equipList, Object... params) {
		// 1. 단독 처리 이벤트   
		EventResultSet eventResult = this.publishRequestBoxEvent(batch, equipList, params);
		
		// 2. 다음 처리 취소 일 경우 결과 리턴 
		if(eventResult.isExecuted()) {
			return ValueUtil.toInteger(eventResult.getResult());
		}
		
		return 0;
	}
	
	/**
	 * 작업 병합 처리
	 * 
	 * @param batch
	 * @param equipList
	 * @param params
	 * @return
	 */
	private int doMergeBatch(JobBatch mainBatch, JobBatch newBatch, List<?> equipList, Object... params) {
		// 1. 전처리 이벤트   
		EventResultSet befResult = this.publishMergingEvent(EventConstants.EVENT_STEP_BEFORE, mainBatch, newBatch, equipList, params);
		
		// 2. 다음 처리 취소 일 경우 결과 리턴 
		if(befResult.isAfterEventCancel()) {
			return ValueUtil.toInteger(befResult.getResult());
		}
		
		// 3. 배치 병합 처리 
		int resultCnt = this.processMerging(mainBatch, newBatch, params);
		
		// 4. 후처리 이벤트 
		EventResultSet aftResult = this.publishMergingEvent(EventConstants.EVENT_STEP_AFTER, mainBatch, newBatch, equipList, params);
		
		// 5. 후처리 이벤트가 실행 되고 리턴 결과가 있으면 해당 결과 리턴 
		if(aftResult.isExecuted()) {
			if(aftResult.getResult() != null) { 
				resultCnt += ValueUtil.toInteger(aftResult.getResult());
			}
		}

		return resultCnt;
	}
	
	/**
	 * 작업 병합 처리
	 * 
	 * @param mainBatch
	 * @param newBatch
	 * @param params
	 * @return
	 */
	private int processMerging(JobBatch mainBatch, JobBatch newBatch, Object ... params) {
		// 1. 단포 작업 활성화 여부 
		boolean useSinglePack = DpsBatchJobConfigUtil.isSingleSkuNpcsClassEnabled(mainBatch);
		// 2. 재고 적치 추천 셀 사용 유무 
		boolean useRecommendCell = DpsBatchJobConfigUtil.isRecommendCellEnabled(mainBatch);
		// 3. 호기별 배치 분리 여부
		boolean useSeparatedBatch = DpsBatchJobConfigUtil.isSeparatedBatchByRack(mainBatch);

		// 4. 인풋 파라미터 설정
		Map<String, Object> inputParams = ValueUtil.newMap("P_IN_DOMAIN_ID,P_IN_BATCH_ID,P_IN_SINGLE_PACK,P_IN_RECOMMEND_CELL,P_IN_SEPARATE_BATCH"
				, mainBatch.getDomainId(), mainBatch.getId(), newBatch.getId(), useSinglePack, useRecommendCell, useSeparatedBatch);
		// 5. 프로시져 콜 
		this.queryManager.callReturnProcedure("OP_DPS_BATCH_MERGE", inputParams, Map.class);
		
		return 1;
	}
		
	/******************************************************************
	 * 							이벤트 전송
	/******************************************************************/
	

	/**
	 * 대상 분류 이벤트 전송
	 * 
	 * @param eventStep
	 * @param batch
	 * @param equipList
	 * @param params
	 * @return
	 */
	private EventResultSet publishClassificationEvent(short eventStep, JobBatch batch, List<?> equipList, Object... params) {
		return this.publishInstructEvent(batch.getDomainId(), EventConstants.EVENT_INSTRUCT_TYPE_CLASSIFICATION, eventStep, batch, equipList, params);
	}
	
	/**
	 * 박스 요청 이벤트 전송
	 * 
	 * @param batch
	 * @param equipList
	 * @param params
	 * @return
	 */
	private EventResultSet publishRequestBoxEvent(JobBatch batch, List<?> equipList, Object... params) {
		return this.publishInstructEvent(batch.getDomainId(), EventConstants.EVENT_INSTRUCT_TYPE_BOX_REQ, EventConstants.EVENT_STEP_ALONE, batch, equipList, params);
	}
		
	/**
	 * 작업 지시 이벤트 전송
	 * 
	 * @param eventStep
	 * @param batch
	 * @param equipList
	 * @param params
	 * @return
	 */
	private EventResultSet publishInstructionEvent(short eventStep, JobBatch batch, List<?> equipList, Object... params) {
		return this.publishInstructEvent(batch.getDomainId(), EventConstants.EVENT_INSTRUCT_TYPE_INSTRUCT, eventStep, batch, equipList, params);
	}
	
	/**
	 * 작업 병합 이벤트 전송
	 * 
	 * @param eventStep
	 * @param mainBatch
	 * @param newBatch
	 * @param equipList
	 * @param params
	 * @return
	 */
	private EventResultSet publishMergingEvent(short eventStep, JobBatch mainBatch, JobBatch newBatch, List<?> equipList, Object... params) {
		return this.publishInstructEvent(mainBatch.getDomainId(), EventConstants.EVENT_INSTRUCT_TYPE_MERGE, eventStep, mainBatch, equipList, newBatch, params);
	}
	
	/**
	 * 추천 로케이션 이벤트 전송
	 * 
	 * @param eventStep
	 * @param batch
	 * @param equipList
	 * @param params
	 * @return
	 */
	private EventResultSet publishRecommendCellsEvent(short eventStep, JobBatch batch, List<?> equipList, Object... params) {
		return this.publishInstructEvent(batch.getDomainId(), EventConstants.EVENT_INSTRUCT_TYPE_RECOMMEND_CELLS, eventStep, batch, equipList, params);
	}
	
	/**
	 * 토털 피킹 이벤트 전송
	 * 
	 * @param eventStep
	 * @param mainBatch
	 * @param equipList
	 * @param params
	 * @return
	 */
	private EventResultSet publishTotalPickingEvent(short eventStep, JobBatch batch, List<?> equipList, Object... params) {
		return this.publishInstructEvent(batch.getDomainId(), EventConstants.EVENT_INSTRUCT_TYPE_TOTAL_PICKING, eventStep, batch, equipList, params);
	}
	
	/**
	 * 작업 지시 이벤트 전송 공통
	 * 
	 * @param domainId
	 * @param eventType
	 * @param eventStep
	 * @param batch
	 * @param equipList
	 * @param params
	 * @return
	 */
	private EventResultSet publishInstructEvent(long domainId, short eventType, short eventStep, JobBatch batch, List<?> equipList, Object... params) {
		// 1. 이벤트 생성 
		BatchInstructEvent event = new BatchInstructEvent(domainId, eventType, eventStep);
		event.setJobBatch(batch);
		event.setJobType(batch.getJobType());
		event.setEquipType(batch.getEquipType());
		event.setEquipList(equipList);
		event.setPayLoad(params);
		
		// 2. event publish
		event = (BatchInstructEvent)this.eventPublisher.publishEvent(event);
		return event.getEventResultSet();
	}

}
