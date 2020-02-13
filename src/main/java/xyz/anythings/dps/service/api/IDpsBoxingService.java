package xyz.anythings.dps.service.api;

import java.util.List;

import xyz.anythings.base.service.api.IBoxingService;

/**
 * DPS 박싱 서비스
 * 
 * @author shortstop
 */
public interface IDpsBoxingService extends IBoxingService {
	
	/**
	 * 주문 정보에 의한 박스 내품 업데이트 
	 * 
	 * @param domainId
	 * @param boxPackId
	 * @param orderIds
	 */
	public void updateBoxItemsByOrder(Long domainId, String boxPackId, List<String> orderIds);
	
	// TODO 단포 박싱 처리 추가 ...
}