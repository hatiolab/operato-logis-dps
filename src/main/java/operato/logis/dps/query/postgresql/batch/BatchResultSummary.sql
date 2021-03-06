SELECT
	PLAN_ORDER,
	ACTUAL_ORDER,
	ACTUAL_BOX AS ACTUAL_SKU,
	ACTUAL_PCS,
	ROUND(ACTUAL_BOX * 100.0 / PLAN_ORDER, 1) AS RATE_ORDER,
	CASE WHEN RUN_HOUR = 0 THEN 0 ELSE ROUND(ACTUAL_PCS / RUN_HOUR, 1) END AS UPH,
	RUN_MIN AS RATE_SKU
FROM (
	SELECT
		A.PLAN_ORDER,
		COALESCE(B.ACTUAL_ORDER, 0) AS ACTUAL_ORDER,
		COALESCE(B.ACTUAL_BOX, 0) AS ACTUAL_BOX,
		A.PICKED_QTY AS ACTUAL_PCS,
		ROUND(extract(epoch from (A.PICK_ENDED_AT - A.PICK_STARTED_AT) / 3600)::integer, 1) AS RUN_HOUR,
		ROUND(extract(epoch from (A.PICK_ENDED_AT - A.PICK_STARTED_AT) / 60)::integer, 1) AS RUN_MIN
	FROM ( 
		(SELECT
			BATCH_ID,
			COUNT(DISTINCT(CLASS_CD)) AS PLAN_ORDER,
			SUM(ORDER_QTY) AS PICK_QTY,
			COALESCE(SUM(PICKED_QTY), 0) AS PICKED_QTY,
			MIN(UPDATED_AT) AS PICK_STARTED_AT,
			MAX(UPDATED_AT) AS PICK_ENDED_AT
		FROM
			ORDERS
		WHERE
			DOMAIN_ID = :domainId
			AND BATCH_ID = :batchId
		GROUP BY
			BATCH_ID
		) A
		
		LEFT OUTER JOIN
		
		(SELECT
			BATCH_ID,
			COUNT(DISTINCT (CUST_ORDER_NO)) ACTUAL_ORDER,
			COUNT(DISTINCT (CLASS_CD)) ACTUAL_BOX
		FROM
			BOX_PACKS
		WHERE
			DOMAIN_ID = :domainId
			AND BATCH_ID = :batchId
			AND STATUS NOT IN ('A', 'W')
		GROUP BY
			BATCH_ID) B
		
		ON A.BATCH_ID = B.BATCH_ID
	)
) X