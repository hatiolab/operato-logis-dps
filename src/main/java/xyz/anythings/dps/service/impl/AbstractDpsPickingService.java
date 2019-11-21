package xyz.anythings.dps.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import xyz.anythings.base.entity.BoxPack;
import xyz.anythings.base.entity.BoxType;
import xyz.anythings.base.entity.Cell;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobConfigSet;
import xyz.anythings.base.entity.JobInput;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.entity.Stock;
import xyz.anythings.base.entity.TrayBox;
import xyz.anythings.base.entity.ifc.IBucket;
import xyz.anythings.base.event.ICategorizeEvent;
import xyz.anythings.base.event.IClassifyErrorEvent;
import xyz.anythings.base.event.IClassifyInEvent;
import xyz.anythings.base.event.IClassifyOutEvent;
import xyz.anythings.base.event.IClassifyRunEvent;
import xyz.anythings.base.model.Category;
import xyz.anythings.base.query.store.BatchQueryStore;
import xyz.anythings.base.query.store.BoxQueryStore;
import xyz.anythings.base.service.api.IBoxingService;
import xyz.anythings.base.service.api.IPickingService;
import xyz.anythings.dps.DpsCodeConstants;
import xyz.anythings.dps.DpsConstants;
import xyz.anythings.dps.service.DpsBoxingService;
import xyz.anythings.dps.service.DpsJobStatusService;
import xyz.anythings.dps.service.util.DpsBatchJobConfigUtil;
import xyz.anythings.dps.service.util.DpsServiceUtil;
import xyz.anythings.sys.model.BaseResponse;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.orm.IQueryManager;
import xyz.elidom.sys.entity.User;
import xyz.elidom.sys.util.DateUtil;
import xyz.elidom.sys.util.MessageUtil;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.util.BeanUtil;
import xyz.elidom.util.ValueUtil;

public abstract class AbstractDpsPickingService implements IPickingService {
	
	/**
	 * 쿼리 매니저 
	 */
	@Autowired
	protected IQueryManager queryManager;
	
	/**
	 * 박싱 서비스 
	 */
	@Autowired
	protected DpsBoxingService dpsBoxingService;
	
	/**
	 * DPS 작업 상태 서비스 
	 */
	@Autowired
	protected DpsJobStatusService dpsJobStatusService;
	
	
	@Autowired
	protected BatchQueryStore batchQueryStore;
	

	/***********************************************************************************************/
	/*   분류 모듈 정보    */
	/***********************************************************************************************/

	/**
	 * 1-1. 분류 모듈 정보 : 분류 서비스 모듈의 작업 유형 (DAS, RTN, DPS, QPS) 리턴 
	 * 
	 * @return
	 */
	@Override
	public String getJobType() {
		return DpsConstants.JOB_TYPE_DPS;
	}

	/**
	 * 1-2. 분류 모듈 정보 : 작업 배치별 작업 설정 정보
	 * 
	 * @param batchId
	 * @return
	 */
	@Override
	public JobConfigSet getJobConfigSet(String batchId) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * 1-3. 분류 모듈 정보 : 작업 배치별 표시기 설정 정보
	 * 
	 * @param batchId
	 * @return
	 */
	/*@Override
	public IndConfigSet getIndConfigSet(String batchId) {
		// TODO Auto-generated method stub
		return null;
	}*/
	
	/**
	 * 1-4. 모듈별 박싱 처리 서비스
	 * 
	 * @param params
	 * @return
	 */
	@Override
	public IBoxingService getBoxingService(Object... params) {
		return this.dpsBoxingService;
	}
	
	
	/***********************************************************************************************/
	/*   중분류    */
	/***********************************************************************************************/
	
	/**
	 * 중분류 이벤트
	 *  
	 * @param event
	 * @return
	 */
	@Override
	public Category categorize(ICategorizeEvent event) {
		// DPS 는 중분류 없음 
		return null;
	};
	
	/***********************************************************************************************/
	/*   버킷 투입    */
	/***********************************************************************************************/

	/**
	 * 2-1. 투입 ID로 유효성 체크 및 투입 유형을 찾아서 리턴 
	 * 
	 * @param domainId
	 * @param inputId
	 * @param params
	 * @return LogisCodeConstants.CLASSIFICATION_INPUT_TYPE_...
	 */
	@Override
	public String checkInput(Long domainId, String inputId, Object ... params) {
		return null;
	};
	/**
	 * 2-2. 분류 설비에 투입 처리
	 * 
	 * @param inputEvent
	 */
	@Override
	public void input(IClassifyInEvent inputEvent) {

	}


	/***********************************************************************************************/
	/*   소분류   */
	/***********************************************************************************************/

	/**
	 * 3-1. 소분류 : 분류 처리 작업
	 * 
	 * @param exeEvent 분류 처리 이벤트
	 * @return
	 */
	@Override
	public Object classify(IClassifyRunEvent exeEvent) {
		switch (exeEvent.getClassifyAction()) {
			// 확정처리 
			case DpsCodeConstants.CLASSIFICATION_ACTION_CONFIRM  :
				this.confirmPick(exeEvent);
				break;
			// 수정 처리 
			case DpsCodeConstants.CLASSIFICATION_ACTION_MODIFY  :
				this.splitPick(exeEvent);
				break;
			// 취소 처리 
			case DpsCodeConstants.CLASSIFICATION_ACTION_CANCEL  :
				this.cancelPick(exeEvent);
				break;
			default : 
				return new BaseResponse(false, null);
		}
		
		return new BaseResponse(true, null);
	}
	
	
	/**
	 * 3-2. 소분류 : 분류 처리 결과 처리 (DAS, DPS, 반품 - 풀 박스 처리 후 호출, 소터 - 단위 상품별 분류 처리 시 I/F로 넘어온 후 호출)
	 * 
	 * @param outputEvent
	 * @return
	 */
	@Override
	public Object output(IClassifyOutEvent outputEvent) {
		return null;
	}

	/**
	 * 3-6. 소분류 : 피킹 확정 처리된 작업 취소
	 * 
	 * @param exeEvent 분류 작업 이벤트
	 * @return
	 */
	@Override
	public int undoPick(IClassifyRunEvent exeEvent) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	/**
	 * 3-7. 소분류 : 박스 처리
	 * 
	 * @param exeEvent 분류 작업 이벤트
	 * @return
	 */
	@Override
	public BoxPack fullBoxing(IClassifyRunEvent exeEvent) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * 3-8. 소분류 : Boxing 취소
	 * 
	 * @param domainId
	 * @param boxPackId
	 * @return
	 */
	@Override
	public BoxPack cancelBoxing(Long domainId, String boxPackId) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * 3-9. 소분류 : 주문별 박스별 피킹 완료 여부 체크
	 * 
	 * @param batch
	 * @param orderId
	 * @param boxId
	 * @return
	 */
	@Override
	public boolean checkBoxingEnd(JobBatch batch, String orderId, String boxId) {
		// TODO Auto-generated method stub
		return false;
	}


	/**
	 * 3-10. 소분류 : 스테이션에 투입된 주문별 피킹 작업 완료 여부 체크
	 * 
	 * @param batch
	 * @param stationCd
	 * @param job
	 * @return
	 */
	@Override
	public boolean checkStationJobsEnd(JobBatch batch, String stationCd, JobInstance job) {
		// TODO Auto-generated method stub
		return false;
	}
	
	/***********************************************************************************************/
	/*   기타   */
	/***********************************************************************************************/

	
	/**
	 * 4-1. 기타 : 배치 내 모든 분류 작업이 완료되었는지 여부 
	 * 
	 * @param batchId
	 * @return
	 */
	@Override
	public boolean checkEndClassifyAll(String batchId) {
		return false;
	}
		
	/**
	 * 4-2. 기타 : 분류 서비스 모듈별 작업 시작 중 추가 처리
	 * 
	 * @param batch
	 */
	@Override
	public void batchStartAction(JobBatch batch) {
	}
	
	/**
	 * 4-3. 기타 : 분류 서비스 모듈별 작업 마감 중 추가 처리
	 * 
	 * @param batch
	 */
	@Override
	public void batchCloseAction(JobBatch batch) {
	}
	
	/**
	 * 4-4. 기타 : 분류 작업 처리시 에러 핸들링
	 * 
	 * @param errorEvent
	 */
	@Override
	public void handleClassifyException(IClassifyErrorEvent errorEvent) {
	}
	
	/***********************************************************************************************/
	/**   Protected    **/
	/***********************************************************************************************/

	
	/***********************************************************************************************/
	/*  2. 버킷 투입    */
	/***********************************************************************************************/

	/**
	 * 박스 패킹 정보 생성 
	 * @param domainId
	 * @param batch
	 * @param bucket
	 * @param orderNo
	 * @param boxPackId
	 */
	protected void createBoxPackData(Long domainId, JobBatch batch, IBucket bucket, String orderNo, String boxPackId) {
		// 1. boxItems 생성 
		this.dpsBoxingService.createBoxItemsDataByOrder(domainId, batch, orderNo, boxPackId);
		
		// 2. boxItem 정보로 boxPack 생성  
		this.dpsBoxingService.createBoxPackDataByBoxItems(domainId, batch, orderNo, bucket.getBucketType(), bucket.getBucketTypeCd(), boxPackId);
	}
	
	/**
	 * 박스 투입 후 액션 
	 * @param domainId
	 * @param batch
	 * @param jobList
	 */
	protected void afterInputEmptyBucket(Long domainId, JobBatch batch, IBucket bucket, List<JobInstance> jobList) {
		// 1. tray 박스인 경우 tray 상태 업데트 
		if(ValueUtil.isEqualIgnoreCase(DpsCodeConstants.BOX_TYPE_TRAY, bucket.getBucketType())) {
			TrayBox tray = (TrayBox)bucket;
			tray.setStatus(DpsConstants.COMMON_STATUS_INPUT);
			this.queryManager.update(tray, DpsConstants.ENTITY_FIELD_STATUS);
		}
		
		// TODO 박스 경로 IF
		
		// TODO 투입된 호기에만 리프레쉬 메시지 전송
	}
	
	/**
	 * 박스 혹은 트레이를 작업에 투입 
	 * @param domainId
	 * @param batch
	 * @param orderNo
	 * @param bucket
	 * @param indColor
	 * @param jobList
	 * @return
	 */
	protected void doInputEmptyBucket(Long domainId, JobBatch batch, String orderNo, IBucket bucket, String indColor, List<JobInstance> jobList, String boxPackId) {
		
		// 1. JobInstance 에서 equipCd, stationCd 가져오기
		// 1.1. 쿼리 
		String newInputsQuery = this.batchQueryStore.getRackDpsBatchNewInputDataQuery();
		
		// 1.2. 파라미터 
		Map<String,Object> params = ValueUtil.newMap("domainId,batchId,equipType,orderNo", domainId,batch.getId(), batch.getEquipType(), orderNo);
		
		// 1.3. 조회 
		List<JobInput> inputList = AnyEntityUtil.searchItems(domainId, false, JobInput.class, newInputsQuery, params);
		
		// 2. 투입 번호 셋팅 및 input 데이터 생성  
		// 2.1 주문 - 박스 ID 매핑 쿼리 
		String mapInstanceBox = this.batchQueryStore.getRackDpsBatchMapBoxIdAndSeqQuery();
		Map<String,Object> mappingParams 
			= ValueUtil.newMap("domainId,batchId,equipType,orderNo,userId,boxId,colorCd,status,inputAt,boxPackId"
				, domainId,batch.getId(), batch.getEquipType(), orderNo, User.currentUser().getId(),bucket.getBucketCd(),indColor,DpsConstants.JOB_STATUS_INPUT,DateUtil.currentTimeStr(),boxPackId);
		
		for(JobInput input : inputList ) {
			// 2.1. 다음 투입 번호
			int newSeq = this.dpsJobStatusService.findNextInputSeq(batch);
			
			// 2.2 새로운 투입 정보 생성
			input.setComCd(batch.getComCd());
			input.setInputSeq(newSeq);
			input.setBoxId(bucket.getBucketCd());
			input.setBoxType(bucket.getBucketType());
			input.setColorCd(indColor);
			input.setInputType(DpsCodeConstants.JOB_INPUT_TYPE_PCS); // TODO : config 처리 필요 
			input.setStatus(DpsCodeConstants.JOB_INPUT_STATUS_WAIT);
			
			
			// 2.3. 주문 - 박스 ID 매핑 params 셋 
			mappingParams.put("inputSeq", newSeq);
			mappingParams.put("stationCd", input.getStationCd());
			
			// 2.4. 주문 - 박스 ID 매핑 Update 실행  
			this.queryManager.executeBySql(mapInstanceBox, mappingParams);
		}
		
		// 2.3 insert batch
		this.queryManager.insertBatch(inputList);
	}
	
	
	/**
	 * 버킷 투입 전 액션 
	 * @param domainId
	 * @param batch
	 * @param isBox
	 * @param bucket
	 * @return
	 */
	protected String beforeInputEmptyBucket(Long domainId, JobBatch batch, boolean isBox, IBucket bucket) {
		
		// 1.버킷의 투입 가능 여부 확인 
		boolean usedBox = false;
		if(isBox) {
			// 1.1 박스는 쿼리를 해서 확인 
			usedBox = this.checkUniqueBoxId(domainId, batch, bucket.getBucketCd());
		} else {
			// 1.2 트레이는 상태가 WAIT 인 트레이만 사용 가능 
			if(ValueUtil.isNotEqual(bucket.getStatus(), DpsConstants.COMMON_STATUS_WAIT)) {
				usedBox = true;
			}
		}
		// 2. 중복되는 버킷이 있으면 사용중 이면 불가 
		if(usedBox) {
			// 박스 / 트레이 은(는) 이미 투입 상태입니다
			String bucketStr = isBox ? "terms.label.box" : "terms.label.tray";
			throw ThrowUtil.newValidationErrorWithNoLog(ThrowUtil.translateMessage("A_ALREADY_B_STATUS", bucketStr, "terms.label.input"));
		}
		
		// TODO 현재는 주문에만 맵핑 하도록 구현 
		// 추후 맵핑 컬럼 적용 필요함. 
		// 3. 박스와 매핑하고자 하는 작업 정보를 조회한다.
		String nextOrderId = this.findNextMappingJob(domainId, batch, bucket.getBucketTypeCd()
				, DpsCodeConstants.DPS_PREPROCESS_COL_ORDER, DpsCodeConstants.DPS_ORDER_TYPE_MT);
		
		if(ValueUtil.isEmpty(nextOrderId)) {
			// 박스에 할당할 주문 정보가 존재하지 않습니다
			throw new ElidomRuntimeException(MessageUtil.getMessage("MPS_NO_ORDER_TO_ASSIGN_BOX"));
		}
		
		// 3. 이미 처리된 주문인지 한 번 더 체크
		if(AnyEntityUtil.selectSizeByEntity(domainId, JobInput.class, "batchId,orderNo", batch.getId(), nextOrderId)>0) {
			// 주문 은(는) 이미 투입 상태입니다
			throw new ElidomRuntimeException(ThrowUtil.translateMessage("A_ALREADY_B_STATUS", "terms.label.order", "terms.label.input"));
		}
		
		return nextOrderId;		
	}
	
	/**
	 * 버킷이 투입 가능한 버킷인 지 확인 및 해당 버킷에 Locking
	 * 
	 * @param domainId
	 * @param batch
	 * @param bucketCd
	 * @param inputType
	 */
	protected IBucket vaildInputBucketByBucketCd(Long domainId, JobBatch batch, String bucketCd, boolean isBox, boolean withLock) {
		
		// 1. 박스 타입이면 박스 에서 조회 
		if(isBox) {
			String boxTypeCd = getBoxTypeByBoxId(batch, bucketCd);
			BoxType boxType = DpsServiceUtil.findBoxType(domainId, boxTypeCd, withLock, true);
			boxType.setBoxId(bucketCd);
			return boxType;
			
		// 2. 트레이 타입이면 트레이에서 조회 
		} else {
			return DpsServiceUtil.findTrayBox(domainId, bucketCd, withLock, true);
		}
	}

	/**
	 * 배치 설정에 박스 아이디 유니크 범위로 중복 여부 확인 
	 * 
	 * @param domainId
	 * @param batch
	 * @param boxId
	 * @return
	 */
	private boolean checkUniqueBoxId(Long domainId, JobBatch batch, String boxId) {
		// 1. 박스 아이디 유니크 범위 설정, TODO API에 기본값 및 존재하지 않는 경우 예외 발생 여부 옵션 필요 
		String uniqueScope = DpsBatchJobConfigUtil.getBoxIdUniqueScope(batch, false);
		
		// 1.1. 설정 값이 없으면 기본 GLOBAL
		if(ValueUtil.isEmpty(uniqueScope)) {
			uniqueScope = DpsConstants.BOX_ID_UNIQUE_SCOPE_GLOBAL;
		}
		
		// 2. 파라미터 셋팅 
		Map<String,Object> params = ValueUtil.newMap("domainId,boxId,batchId,uniqueScope"
				, domainId, boxId, batch.getId(), uniqueScope);
		
		// 3. 쿼리 
		String qry = BeanUtil.get(BoxQueryStore.class).getBoxIdUniqueCheckQuery();
		
		// 4. 조회 dup Cnt == 0  중복 없음 
		int dupCnt = BeanUtil.get(IQueryManager.class).selectBySql(qry, params, Integer.class);

		return dupCnt == 0 ? false : true;
	}
	
	/**
	 * boxId 에서 박스 타입 구하기
	 * 
	 * @param batch
	 * @param boxId
	 * @return
	 */
	private String getBoxTypeByBoxId(JobBatch batch, String boxId) {
		// 1. 박스 ID 에 박스 타입 split 기준, TODO BatchJobConfigUtil 메소드 호출로 수정 
		// DpsConfigConstants.DPS_INPUT_BOX_TYPE_SPLIT_INDEX);
		String boxTypeSplit = "0,1";
		
		// 2. 설정 값이 없으면 기본 0,1
		if(ValueUtil.isEmpty(boxTypeSplit) || boxTypeSplit.length() < 3) {
			boxTypeSplit = "0,1";
		}
		
		// 3. 기준에 따라 박스 타입 분할 
		String[] splitIndex = boxTypeSplit.split(DpsConstants.COMMA);
		String boxType = boxId.substring(ValueUtil.toInteger(splitIndex[0]), ValueUtil.toInteger(splitIndex[1]));
		return boxType;
	}
	
	
	
	/**
	 * 다음 맵핑 할 작업을 찾는다.
	 * @param domainId
	 * @param batch
	 * @param bucketType
	 * @return
	 */
	private String findNextMappingJob(Long domainId, JobBatch batch, String boxTypeCd, String mapColumn, String orderType){
		// TODO : order / shop .... 여러타입에 대한 구현 필요 
		// 실제 DPS 기준 주문 가공 프로세스임.
		// 주문 처리 순서를 정할수 있는 부분으로 쿼리에 대한 수정으로 해결 가능 ? 
		
		// 1. 쿼리 
		String qry =  this.batchQueryStore.getFindNextMappingJobQuery();
		
		// 2. 파라민터 
		Map<String,Object> params = ValueUtil.newMap("domainId,mapColumn,batchId,orderType,boxTypeCd"
				, domainId, mapColumn, batch.getId(), orderType, boxTypeCd);
		
		// 3. 조회 ( 맵핑 기준에 따라 결과가 달라짐 )
		return AnyEntityUtil.findItem(domainId, false, String.class, qry, params);
	}
	
	
	/***********************************************************************************************/
	/*  3. 소분류    */
	/***********************************************************************************************/

	/**
	 * 소분류 작업 처리 전 처리 액션
	 * @param batch
	 * @param job
	 * @param cell
	 * @param pickQty
	 * @return 
	 */
	protected int beforeConfirmPick(long domainId, JobBatch batch, JobInstance job, Cell cell, int pickQty) {
		// 1. 작업이 이미 완료되었다면 리턴
		if(job.isDoneJob()) {
			return 0;
		// 2. 이미 모두 처리되었다면 스킵
		} else if(job.getPickedQty() >= job.getPickQty()) {
			return 0;
		}
		
		// 3. 피킹 수량 보정 - 주문 수량 보다 처리 수량이 큰경우 차이값만큼만 처리 
		if(job.getPickedQty() + pickQty > job.getPickQty()) {
			pickQty = job.getPickQty() - job.getPickedQty();
		}
		
		// 3. 피킹 수량 리턴
		return pickQty;
	}
	
	/**
	 * 소분류 작업 처리 
	 * @param batch
	 * @param job
	 * @param cell
	 * @param pickQty
	 */
	protected void doConfirmPick(long domainId, JobBatch batch, JobInstance job, Cell cell, int pickQty) {
		// 1. 피킹 작업 처리
		job.setStatus(DpsConstants.JOB_STATUS_FINISH);
		job.setPickEndedAt(DateUtil.currentTimeStr());
		job.setPickedQty(pickQty);
		job.setPickingQty(0);
		job.setUpdatedAt(new Date());
		
		this.queryManager.update(job, DpsConstants.ENTITY_FIELD_STATUS, "pickEndedAt", "pickedQty", "pickingQty", DpsConstants.ENTITY_FIELD_UPDATED_AT);

		// 2. 합포의 경우 에만 
		if(ValueUtil.isEqualIgnoreCase(job.getOrderType(), DpsCodeConstants.DPS_ORDER_TYPE_MT)) {
			// 2.1 Lock 을 걸고 재고 조회 
			// TODO : BIN 인덱스 
			Stock stock = AnyEntityUtil.findEntityBy(domainId, true, true, Stock.class, null, "equipType,equipCd,cellCd,comCd,skuCd", job.getEquipType(),job.getEquipCd(),job.getSubEquipCd(),job.getComCd(),job.getSkuCd());
			stock.setAllocQty(stock.getAllocQty() - pickQty);
			stock.setPickedQty(stock.getPickedQty() + pickQty);
			stock.setUpdatedAt(new Date());
			
			// 2.2 재고 업데이트 
			this.queryManager.update(stock, "allocQty", "pickedQty", DpsConstants.ENTITY_FIELD_UPDATED_AT);
			
			// 2.3 TODO : 같은 cell 에 다른 bin 의 상품이 걸려 있는 경우 처리 필요 
			// 지시기 점등 ? 
		}
	}

	/**
	 * 소분류 작업 처리 후처리 액션  
	 * @param batch
	 * @param job
	 * @param cell
	 * @param resQty
	 */
	protected void afterComfirmPick(long domainId, JobBatch batch, JobInstance job, Cell cell, Integer resQty) {
		
		Map<String,Object> params = ValueUtil.newMap("domainId,batchId,orderNo,comCd,skuCd,resQty", domainId, batch.getId(), job.getOrderNo(), job.getComCd(), job.getSkuCd(), resQty);
		
		// 1. 주문 확정 수량 업데이트
		String findOrderList = this.batchQueryStore.getFindOrderQtyUpdateListQuery();
		List<Order> orderList = AnyEntityUtil.searchItems(domainId, false, Order.class, findOrderList, params) ;
		
		int pickedQty = resQty;
		int orderPickedQty = 0;
		
		Date now = new Date();
		List<String> updateOrderList = new ArrayList<String>();
		
		for(Order order : orderList) {
			if(pickedQty > order.getOrderQty()) orderPickedQty = order.getOrderQty();
			else orderPickedQty = pickedQty;
			
			order.setStatus(Order.STATUS_RUNNING);
			order.setUpdatedAt(now);
			order.setPickedQty(order.getPickedQty() + orderPickedQty);
			
			pickedQty -= orderPickedQty;
			updateOrderList.add(order.getId());
		}
		
		this.queryManager.updateBatch(orderList, DpsConstants.ENTITY_FIELD_STATUS,DpsConstants.ENTITY_FIELD_UPDATED_AT,"pickedQty");
		
		// 2. 박스 실적 데이터의 상태를 '작업 중' (혹은 '피킹 중')으로 변경
		BoxPack boxPack = AnyEntityUtil.findEntityById(true, BoxPack.class, job.getBoxPackId());
		boxPack.setPickedQty(boxPack.getPickedQty() + resQty);
		boxPack.setUpdatedAt(now);
		
		this.queryManager.update(boxPack,DpsConstants.ENTITY_FIELD_UPDATED_AT,"pickedQty");
		
		// 3. 박스 상세 데이터의 picked_qty를 올리고 주문 수량 만큼 분류가 끝났는지 체크하여 상태를 '분류 중'으로 변경한다.
		this.dpsBoxingService.updateBoxItemDataByOrder(domainId, job.getBoxPackId(), updateOrderList);
		
		// 4. TODO : 표시기에 해당 몇 개 처리되었는지 표시 ?? 
		
		// 5. 작업이 끝난 후 Station 의 JobInput Data Update  
		this.updateJobInputEndStatusWithLock(domainId, batch, job, cell);
		
		// 6. TODO : 모바일 새로고침 명령 전달
//		RefreshEvent event = new RefreshEvent(job.getDomainId(), job.getJobType(),location.getRegionCd(), location.getZoneCd(),null, RefreshEvent.REFRESH_DETAILS);
//	    this.eventPublisher.publishEvent(event);
	}
	
	/**
	 * 작업 시퀀스에 대한 존의 작업 완료 여부를 판별해 상태 Update 
	 * @param domainId
	 * @param batch
	 * @param job
	 * @param cell
	 */
	private void updateJobInputEndStatusWithLock(long domainId, JobBatch batch, JobInstance job, Cell cell) {
		
		// 1. JobInput 조회 및 Lock 
		JobInput input = AnyEntityUtil.findEntityBy(domainId, true, true, JobInput.class, null
									, "batchId,equipType,equipCd,stationCd,orderNo,inputSeq"
									, batch.getId(), job.getEquipType(), job.getEquipCd(), cell.getStationCd(), job.getOrderNo(), job.getInputSeq());
		
		// 2. 동일 존 내의 Input Seq 가 전부 완료 되었는지 확인 
		int pickingCnt = AnyEntityUtil.selectSizeByEntity(domainId, JobInstance.class
				, "batchId,equipType,equipCd,orderNo,inputSeq,status"
				, batch.getId(), job.getEquipType(), job.getEquipCd(), job.getOrderNo(), job.getInputSeq(), DpsConstants.JOB_STATUS_PICKING);
		
		// 2.1. 완료 여부에 따라 input 데이터 상태 Update 
		if(pickingCnt == 0 ) {
			input.setStatus(DpsCodeConstants.JOB_INPUT_STATUS_FINISHED);
			input.setUpdatedAt(new Date());
			this.queryManager.update(input, DpsConstants.ENTITY_FIELD_STATUS, DpsConstants.ENTITY_FIELD_UPDATED_AT);
		}
	}
}
