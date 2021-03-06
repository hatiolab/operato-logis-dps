SELECT
	X.DOMAIN_ID,
	X.BATCH_ID,
	X.CLASS_CD AS ORDER_NO,
	X.COM_CD,
	X.SKU_CD,
	X.ORDER_QTY
FROM (
	SELECT
		DOMAIN_ID,
		BATCH_ID,
		CLASS_CD,
		COM_CD,
		SKU_CD,
		SUM(ORDER_QTY) AS ORDER_QTY
	FROM
		ORDERS
	WHERE
		DOMAIN_ID = :domainId
		AND BATCH_ID = :batchId
		AND ORDER_TYPE = 'MT'
		AND SKU_CD = :skuCd
		AND STATUS = 'W'
		#if($skipOrderIdList)
		AND CLASS_CD NOT IN (:skipOrderIdList)
		#end
	GROUP BY
		DOMAIN_ID,
		BATCH_ID,
		CLASS_CD,
		COM_CD,
		SKU_CD
) X
WHERE
	X.ORDER_QTY <= :stockQty
ORDER BY
	ORDER_QTY ASC