package operato.logis.dps.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import operato.logis.dps.DpsConstants;
import operato.logis.dps.query.store.DpsBatchQueryStore;
import operato.logis.dps.service.util.DpsStageJobConfigUtil;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.BatchReceipt;
import xyz.anythings.base.entity.BatchReceiptItem;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.entity.OrderPreprocess;
import xyz.anythings.base.event.main.BatchReceiveEvent;
import xyz.anythings.base.service.util.BatchJobConfigUtil;
import xyz.anythings.base.util.LogisBaseUtil;
import xyz.anythings.sys.service.AbstractQueryService;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.anythings.sys.util.AnyValueUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.dbist.util.StringJoiner;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.sys.entity.Domain;
import xyz.elidom.util.BeanUtil;
import xyz.elidom.util.ValueUtil;

/**
 * DPS 주문 수신용 서비스
 * 
 * @author shortstop
 */
@Component
public class DpsReceiveBatchService extends AbstractQueryService {

	/**
	 * 커스텀 서비스 - 주문 수신
	 */
	//private static final String DIY_DPS_RECEIVE_ORDERS = "diy-dps-receive-orders";

	/**
	 * 배치 관련 쿼리 스토어 
	 */
	@Autowired
	private DpsBatchQueryStore batchQueryStore;

	/**
	 * 주문 정보 수신을 위한 수신 서머리 정보 조회
	 *  
	 * @param event
	 */
	@EventListener(classes = BatchReceiveEvent.class, condition = "#event.eventType == 10 and #event.eventStep == 1 and #event.jobType == 'DPS'")
	public void handleReadyToReceive(BatchReceiveEvent event) { 
		BatchReceipt receipt = event.getReceiptData();
		receipt = this.createReadyToReceiveData(receipt);
		event.setReceiptData(receipt);
	}
	
	/**
	 * 배치 수신 서머리 데이터 생성 
	 * 
	 * @param receipt
	 * @param areaCd
	 * @param stageCd
	 * @param comCd
	 * @param jobDate
	 * @param params
	 * @return
	 */
	private BatchReceipt createReadyToReceiveData(BatchReceipt receipt, Object ... params) {
		
		// 1. 대기 상태 이거나 진행 중인 수신이 있는지 확인 
//		BatchReceipt runBatchReceipt = this.checkRunningOrderReceipt(receipt);
//		if(runBatchReceipt != null) return runBatchReceipt;
		
		// 2. WMS IF 테이블에서 수신 대상 데이터 확인
		List<BatchReceiptItem> receiptItems = this.getWmfIfToReceiptItems(receipt);
		
		// 3 수신 아이템 데이터 생성 
		for(BatchReceiptItem item : receiptItems) {
			item.setBatchId(LogisBaseUtil.newReceiptJobBatchId(receipt.getDomainId()));
			item.setBatchReceiptId(receipt.getId());
			this.queryManager.insert(item);
			receipt.addItem(item);
		}
		
		// 4. 배치 수신 결과 리턴
		return receipt;
	}
	
	/**
	 * WMS IF 테이블에서 수신 대상 데이터 확인
	 * 
	 * @param receipt
	 * @return
	 */
	private List<BatchReceiptItem> getWmfIfToReceiptItems(BatchReceipt receipt) {
		Map<String,Object> params = ValueUtil.newMap("domainId,comCd,areaCd,stageCd,jobDate",
				receipt.getDomainId(), receipt.getComCd(), receipt.getAreaCd(), receipt.getStageCd(), receipt.getJobDate());
		return AnyEntityUtil.searchItems(receipt.getDomainId(), false, BatchReceiptItem.class, this.batchQueryStore.getOrderSummaryToReceive(), params);
	}
	
	/**
	 * 대기 상태 이거나 진행 중인 수신이 있는지 확인
	 * 
	 * @param domainId
	 * @return
	 */
//	private BatchReceipt checkRunningOrderReceipt(BatchReceipt receipt) {
//		Map<String,Object> params = ValueUtil.newMap("domainId,comCd,areaCd,stageCd,jobDate,status", 
//				receipt.getDomainId(), receipt.getComCd(), receipt.getAreaCd(), receipt.getStageCd(), receipt.getJobDate(),
//				ValueUtil.newStringList(DpsConstants.COMMON_STATUS_WAIT, DpsConstants.COMMON_STATUS_RUNNING));
//		
//		BatchReceipt receiptData = AnyEntityUtil.findItem(receipt.getDomainId(), false, BatchReceipt.class, this.batchQueryStore.getBatchReceiptOrderTypeStatusQuery(), params);
//		
//		// 대기 중 또는 진행 중인 수신 정보 리턴 
//		if(receiptData != null) {
//			receiptData.setItems(AnyEntityUtil.searchDetails(receipt.getDomainId(), BatchReceiptItem.class, "batchReceiptId", receiptData.getId()));
//			return receiptData;
//		}
//		
//		return null;
//	}
	
	/**
	 * 주문 정보 수신 시작
	 * 
	 * @param event
	 */
	@EventListener(classes = BatchReceiveEvent.class, condition = "#event.eventType == 20 and #event.eventStep == 1 and #event.jobType == 'DPS'")
	public void handleStartToReceive(BatchReceiveEvent event) {
		BatchReceipt receipt = event.getReceiptData();
		List<BatchReceiptItem> items = receipt.getItems();
		
		for(BatchReceiptItem item : items) {
			if(ValueUtil.isEqualIgnoreCase(DpsConstants.JOB_TYPE_DPS, item.getJobType())) {
				this.startToReceiveData(receipt, item);
			}
		}
	}
	
	/*@Async
	public void asyncStartToReceive(BatchReceipt receipt, BatchReceiptItem item) {
		Domain domain = Domain.find(receipt.getDomainId());
		DomainContext.setCurrentDomain(domain);
		
		try {
			this.startToReceiveData(receipt, item);
		} finally {
			DomainContext.unsetAll();
		}
	}*/
	
	/**
	 * 배치, 작업 수신
	 * 
	 * @param receipt
	 * @param params
	 * @return
	 */
	private BatchReceipt startToReceiveData(BatchReceipt receipt, BatchReceiptItem item, Object ... params) {
		
		// 1. 수신 시작 : 상태 업데이트 - 진행중 
		receipt.updateStatusImmediately(DpsConstants.COMMON_STATUS_RUNNING);
		
		// 1.1. 박스 맵핑 설정 필드 가져오기  
		String mappingColumn = DpsStageJobConfigUtil.getBoxMappingTargetField(item.getStageCd());
		
		// TODO : 데이터 복사 방식 / 컬럼 설정에서 가져오기 
		String[] sourceFields = {"WMS_BATCH_NO", "WCS_BATCH_NO", "JOB_DATE", "JOB_SEQ", "JOB_TYPE", "ORDER_DATE", "ORDER_NO", "ORDER_LINE_NO", "ORDER_DETAIL_ID", "CUST_ORDER_NO", "CUST_ORDER_LINE_NO", "COM_CD", "AREA_CD", "STAGE_CD", "EQUIP_TYPE", "EQUIP_CD", "EQUIP_NM", "SUB_EQUIP_CD", "SHOP_CD", "SHOP_NM", "SKU_CD", "SKU_BARCD", "SKU_NM", "BOX_TYPE_CD", "BOX_IN_QTY", "ORDER_QTY", "PICKED_QTY", "BOXED_QTY", "CANCEL_QTY", "BOX_ID", "INVOICE_ID", "ORDER_TYPE", "CLASS_CD", "PACK_TYPE", "LOT_NO", "FROM_ZONE_CD", "FROM_CELL_CD", "TO_ZONE_CD", "TO_CELL_CD", mappingColumn};
		String[] targetFields = {"WMS_BATCH_NO", "WCS_BATCH_NO", "JOB_DATE", "JOB_SEQ", "JOB_TYPE", "ORDER_DATE", "ORDER_NO", "ORDER_LINE_NO", "ORDER_DETAIL_ID", "CUST_ORDER_NO", "CUST_ORDER_LINE_NO", "COM_CD", "AREA_CD", "STAGE_CD", "EQUIP_TYPE", "EQUIP_CD", "EQUIP_NM", "SUB_EQUIP_CD", "SHOP_CD", "SHOP_NM", "SKU_CD", "SKU_BARCD", "SKU_NM", "BOX_TYPE_CD", "BOX_IN_QTY", "ORDER_QTY", "PICKED_QTY", "BOXED_QTY", "CANCEL_QTY", "BOX_ID", "INVOICE_ID", "ORDER_TYPE", "CLASS_CD", "PACK_TYPE", "LOT_NO", "FROM_ZONE_CD", "FROM_CELL_CD", "TO_ZONE_CD", "TO_CELL_CD", "CLASS_CD"};
		String fieldNames = "COM_CD,AREA_CD,STAGE_CD,WMS_BATCH_NO,IF_FLAG";

		// 별도 트랜잭션 처리를 위해 컴포넌트 자신의 레퍼런스 준비
		DpsReceiveBatchService selfSvc = BeanUtil.get(DpsReceiveBatchService.class);
		
		try {
			// 2. skip 이면 pass
			if(item.getSkipFlag()) {
				item.updateStatusImmediately(DpsConstants.COMMON_STATUS_SKIPPED, null);
				return receipt;
			}
			
			// 4. BatchReceiptItem 상태 업데이트  - 진행 중 
			item.updateStatusImmediately(DpsConstants.COMMON_STATUS_RUNNING, null);
			
			// 5. JobBatch 생성 
			JobBatch batch = JobBatch.createJobBatch(item.getBatchId(), item.getJobSeq(), receipt, item);
			
			// 6. 데이터 복사  
			selfSvc.cloneData(item.getBatchId(), item.getJobSeq(), "wms_if_orders", sourceFields, targetFields, fieldNames, item.getComCd(), item.getAreaCd(), item.getStageCd(), item.getWmsBatchNo(), DpsConstants.N_CAP_STRING);
			
			// 7. JobBatch 상태 변경  
			batch.updateStatusImmediately(DpsConstants.isB2CJobType(batch.getJobType())? JobBatch.STATUS_READY : JobBatch.STATUS_WAIT);
			
			// 8. batchReceiptItem 상태 업데이트 
			item.updateStatusImmediately(DpsConstants.COMMON_STATUS_FINISHED, null);
			
		} catch(Throwable th) {
			// 9. 에러 처리 
			selfSvc.handleReceiveError(th, receipt, item);
		}
		
		return receipt;
	}
	
	/**
	 * 데이터 복제
	 * 
	 * @param sourceTable
	 * @param targetTable
	 * @param sourceFields
	 * @param targetFields
	 * @param fieldNames
	 * @param fieldValues
	 * @return
	 */
	@Transactional(propagation=Propagation.REQUIRES_NEW) 
	public void cloneData(String batchId, String jobSeq
								, String sourceTable
								, String[] sourceFields, String[] targetFields
								, String fieldNames, Object ... fieldValues) throws Exception {
		
		// 1. 조회 쿼리 생성  
		StringJoiner qry = new StringJoiner(DpsConstants.LINE_SEPARATOR);
		
		// 1.1 select 필드 셋팅 
		qry.add("select 1 ");
		for(int i = 0 ; i < sourceFields.length ;i++) {
			qry.add(" , " + sourceFields[i] + " as " + targetFields[i]);
		}
		
		// 1.2 테이블 
		qry.add("  from " + sourceTable);
		
		// 1.3 where 조건 생성 
		StringJoiner whereStr = new StringJoiner(DpsConstants.LINE_SEPARATOR);
		String[] keyArr = fieldNames.split(DpsConstants.COMMA);
		
		
		// 1.3.1 치환 가능 하도록 쿼리문 생성 
		whereStr.add("where 1 = 1 ");
		for(String key : keyArr) {
			whereStr.add(" and " + key + " = :" + key);
		}
		qry.add(whereStr.toString());
		
		// 2. 조회  
		Map<String,Object> params = ValueUtil.newMap(fieldNames, fieldValues);
		List<Order> sourceList = AnyEntityUtil.searchItems(Domain.currentDomainId(), false, Order.class, qry.toString(), params);
		List<Order> targetList = new ArrayList<Order>(sourceList.size());
		// 3. target 데이터 생성 
		for(Order sourceItem : sourceList) {
			Order targetItem = AnyValueUtil.populate(sourceItem, new Order());
			
			targetItem.setBatchId(batchId);
			targetItem.setJobSeq(jobSeq);
			targetItem.setStatus(Order.STATUS_WAIT);
			targetList.add(targetItem);
		}
		
		// 4. 데이터 insert 
		AnyOrmUtil.insertBatch(targetList, 100);
	}
	
	/**
	 * 주문 수신시 에러 핸들링
	 * 
	 * @param th
	 * @param receipt
	 * @param item
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW) 
	public void handleReceiveError(Throwable th, BatchReceipt receipt, BatchReceiptItem item) {
		String errMsg = th.getCause() != null ? th.getCause().getMessage() : th.getMessage();
		errMsg = errMsg.length() > 400 ? errMsg.substring(0,400) : errMsg;
		item.updateStatusImmediately(LogisConstants.COMMON_STATUS_ERROR, errMsg);
		receipt.updateStatusImmediately(LogisConstants.COMMON_STATUS_ERROR);
	}
	
	/**
	 * 주문 수신 취소
	 * 
	 * @param event
	 */
	@EventListener(classes = BatchReceiveEvent.class, condition = "#event.eventType == 30 and #event.eventStep == 1 and #event.jobType == 'DPS'")
	public void handleCancelReceived(BatchReceiveEvent event) {
		// 1. 작업 배치 추출 
		JobBatch batch = event.getJobBatch();
		
		// 2. 배치 상태 체크
		String currentStatus = AnyEntityUtil.findItemOneColumn(batch.getDomainId(), true, String.class, JobBatch.class, DpsConstants.ENTITY_FIELD_STATUS, DpsConstants.ENTITY_FIELD_ID, batch.getId());
		
		if(ValueUtil.isNotEqual(currentStatus, JobBatch.STATUS_WAIT) && ValueUtil.isNotEqual(currentStatus, JobBatch.STATUS_READY)) {
			throw new ElidomRuntimeException("작업 대기 상태에서만 취소가 가능 합니다.");
		}
		
		// 3. 주문 취소시 데이터 유지 여부에 따라서
		boolean isKeepData = BatchJobConfigUtil.isDeleteWhenOrderCancel(batch);
		int cancelledCnt = isKeepData ? this.cancelOrderKeepData(batch) : this.cancelOrderDeleteData(batch);
		event.setResult(cancelledCnt);
	}
	
	/**
	 * 주문 데이터 삭제 update
	 * 
	 * seq = 0
	 * @param batch
	 * @return
	 */
	private int cancelOrderKeepData(JobBatch batch) {
		int cnt = 0;
		
		// 1. 배치 상태  update 
		batch.updateStatus(JobBatch.STATUS_CANCEL);
		
		// 2. 주문 조회 
		List<Order> orderList = AnyEntityUtil.searchEntitiesBy(batch.getDomainId(), false, Order.class, DpsConstants.ENTITY_FIELD_ID, "batchId", batch.getId());
		
		// 3. 취소 상태 , seq = 0 셋팅 
		for(Order order : orderList) {
			order.setStatus(Order.STATUS_CANCEL);
			order.setJobSeq("0");
		}
		
		// 4. 배치 update
		AnyOrmUtil.updateBatch(orderList, 100, "jobSeq", DpsConstants.ENTITY_FIELD_STATUS);
		cnt += orderList.size();
		
		// 5. 주문 가공 데이터 삭제  
		cnt += this.deleteBatchPreprocessData(batch);
		return cnt;
	}
	
	/**
	 * 주문 데이터 삭제
	 * 
	 * @param batch
	 * @return
	 */
	private int cancelOrderDeleteData(JobBatch batch) {
		int cnt = 0;
		
		// 1. 삭제 조건 생성 
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		
		// 2. 삭제 실행
		cnt+= this.queryManager.deleteList(Order.class, condition);
		
		// 3. 주문 가공 데이터 삭제 
		cnt += this.deleteBatchPreprocessData(batch);
		
		// 4. 배치 삭제 
		this.queryManager.delete(batch);
		
		return cnt;
	}
	
	/**
	 * 주문 가공 데이터 삭제
	 * 
	 * @param batch
	 * @return
	 */
	private int deleteBatchPreprocessData(JobBatch batch) {
		// 1. 삭제 조건 생성 
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		
		// 2. 삭제 실행
		return this.queryManager.deleteList(OrderPreprocess.class, condition);
	}

}
