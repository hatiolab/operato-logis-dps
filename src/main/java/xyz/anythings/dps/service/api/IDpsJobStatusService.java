package xyz.anythings.dps.service.api;

import java.util.List;

import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.service.api.IJobStatusService;
import xyz.anythings.dps.model.DpsSinglePackSummary;

/**
 * DPS용 작업 상태 조회 서비스 API
 * 
 * @author shortstop
 */
public interface IDpsJobStatusService extends IJobStatusService {

	/**
	 * 단포 작업을 위한 조회 
	 * 
	 * @param batch
	 * @param skuCd
	 * @param boxType
	 * @param jobPcs
	 * @return
	 */
	public List<DpsSinglePackSummary> searchSinglePackInfo(JobBatch batch, String skuCd, String boxType, Integer jobPcs);
}
