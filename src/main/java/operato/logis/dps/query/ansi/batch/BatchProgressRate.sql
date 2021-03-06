WITH T_JOBS AS (
    SELECT A.CLASS_CD, A.COM_CD, A.SKU_CD
         , A.PICK_QTY, A.PICKED_QTY
         , A.PICK_QTY - A.PICKED_QTY AS MINUS_QTY
      FROM (
            SELECT CLASS_CD, COM_CD, SKU_CD
                 , PICK_QTY, NVL(PICKED_QTY, 0) AS PICKED_QTY
              FROM JOB_INSTANCES
             WHERE DOMAIN_ID = :domainId
               AND BATCH_ID = :batchId
               AND EQUIP_TYPE = :equipType
             #if($equipCd)
               AND EQUIP_CD = :equipCd
             #end
             #if($orderType)
               AND ORDER_TYPE = :orderType
             #end
               AND STATUS NOT IN ('C','D') -- 작업 취소 , 주문 취소가 아닌 것 
           ) A
),
T_PCS AS (
    SELECT SUM(PICK_QTY) AS PLAN_PCS, SUM(PICKED_QTY) AS ACTUAL_PCS
      FROM T_JOBS
),
T_SKU AS (
    SELECT COUNT(1) AS PLAN_SKU, SUM(A.ACTUAL_SKU) AS ACTUAL_SKU
      FROM (
            SELECT COM_CD, SKU_CD, DECODE(SUM(PICKED_QTY), SUM(PICK_QTY), 1, 0) AS ACTUAL_SKU
              FROM T_JOBS
             GROUP BY COM_CD, SKU_CD
           ) B
),
T_ORDER AS (
    SELECT COUNT(1) AS PLAN_ORDER, SUM(A.ACTUAL_ORDER) AS ACTUAL_ORDER
      FROM (
            SELECT CLASS_CD, DECODE(SUM(PICKED_QTY), SUM(PICK_QTY), 1, 0) AS ACTUAL_ORDER
              FROM T_JOBS
             GROUP BY CLASS_CD
           ) C
)
SELECT 
	E.PLAN_PCS, 
	E.ACTUAL_PCS, 
	E.PLAN_SKU, 
	E.ACTUAL_SKU, 
	E.PLAN_ORDER, 
	E.ACTUAL_ORDER, 
	DECODE(E.PLAN_PCS, 0 , 0, E.ACTUAL_PCS/E.PLAN_PCS) * 100 AS RATE_PCS, 
	DECODE(E.PLAN_SKU, 0 , 0, E.ACTUAL_SKU/E.PLAN_SKU) * 100 AS RATE_SKU, 
	DECODE(E.PLAN_ORDER, 0 , 0, E.ACTUAL_ORDER/E.PLAN_ORDER) * 100 AS RATE_ORDER
  FROM (
		SELECT SUM(D.PLAN_PCS) AS PLAN_PCS
		     , SUM(D.ACTUAL_PCS) AS ACTUAL_PCS
		     , SUM(D.PLAN_SKU) AS PLAN_SKU
		     , SUM(D.ACTUAL_SKU) AS ACTUAL_SKU
		     , SUM(D.PLAN_ORDER) AS PLAN_ORDER
		     , SUM(D.ACTUAL_ORDER) AS ACTUAL_ORDER
		  FROM (
		        SELECT PLAN_PCS, ACTUAL_PCS
		             , 0 AS PLAN_SKU, 0 AS ACTUAL_SKU
		             , 0 AS PLAN_ORDER, 0 AS ACTUAL_ORDER
		          FROM T_PCS 
		          
		         UNION ALL
		         
		        SELECT 0 AS PLAN_PCS, 0 AS ACTUAL_PCS
		             , PLAN_SKU, ACTUAL_SKU
		             , 0 AS PLAN_ORDER, 0 AS ACTUAL_ORDER
		          FROM T_SKU 
		          
		         UNION ALL
		         
		        SELECT 0 AS PLAN_PCS, 0 AS ACTUAL_PCS
		             , 0 AS PLAN_SKU, 0 AS ACTUAL_SKU
		             , PLAN_ORDER, ACTUAL_ORDER
		          FROM T_ORDER
		       ) D
       ) E
