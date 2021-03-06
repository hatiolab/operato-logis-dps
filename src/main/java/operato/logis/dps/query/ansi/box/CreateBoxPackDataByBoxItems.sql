
INSERT INTO BOX_PACKS (ID, BATCH_ID, WCS_BATCH_NO, WMS_BATCH_NO, JOB_DATE, JOB_SEQ, JOB_TYPE, ORDER_DATE
                    , ORDER_NO, COM_CD, SHOP_CD, SHOP_NM, AREA_CD, STAGE_CD, EQUIP_TYPE, EQUIP_CD, EQUIP_NM
                    , ORDER_TYPE, BOX_TYPE_CD, BOX_ID, SKU_QTY, PICK_QTY, PICKED_QTY
                    , BOX_WT, BOX_WT_MIN, BOX_WT_MAX, PACK_TYPE, CLASS_CD, BOX_CLASS_CD, PASS_FLAG
                    , STATUS, DOMAIN_ID, CREATOR_ID, UPDATER_ID, CREATED_AT, UPDATED_AT)
SELECT :boxPackId AS ID 
     , X.ID AS BATCH_ID
     , X.WCS_BATCH_NO
     , X.WMS_BATCH_NO
     , X.JOB_DATE
     , X.JOB_SEQ
     , X.JOB_TYPE
     , (SELECT DISTINCT ORDER_DATE FROM ORDERS 
         WHERE DOMAIN_ID = :domainId
           AND BATCH_ID = :batchId
           AND ORDER_NO = :orderNo) AS ORDER_DATE
     , :orderNo AS ORDER_NO
     , X.COM_CD
     , K.SHOP_CD
     , K.SHOP_NM
     , X.AREA_CD
     , X.STAGE_CD
     , X.EQUIP_TYPE
     , X.EQUIP_CD
     , X.EQUIP_NM
     , K.ORDER_TYPE
     , K.BOX_TYPE_CD
     , K.BOX_ID
     , Y.SKU_QTY
     , Y.PICK_QTY
     , 0 AS PICKED_QTY
     , Y.SKU_WT + Z.BOX_WT AS BOX_WT
     , Y.SKU_WT + Z.BOX_WT_MIN AS BOX_WT_MIN
     , Y.SKU_WT + Z.BOX_WT_MAX AS BOX_WT_MAX
     , Y.PACK_TYPE AS PACK_TYPE
     , K.CLASS_CD
     , K.BOX_CLASS_CD
     , 0 AS PASS_FLAG     
     , 'W' AS STATUS
     , X.DOMAIN_ID
     , :userId AS CREATOR_ID
     , :userId AS UPDATER_ID
     , SYSDATE AS CREATED_AT
     , SYSDATE AS UPDATED_AT
  FROM (
        SELECT *
          FROM JOB_BATCHES
         WHERE DOMAIN_ID = :domainId
           AND ID = :batchId
       ) X
     , (
        SELECT COUNT(DISTINCT SKU_CD) AS SKU_QTY
             , SUM(PICK_QTY) AS PICK_QTY
             , SUM(SKU_WT * PICK_QTY) AS SKU_WT
             , MAX(PACK_TYPE) AS PACK_TYPE
          FROM BOX_ITEMS
         WHERE DOMAIN_ID = :domainId
           AND BOX_PACK_ID = :boxPackId
       ) Y
     , (
        SELECT SUM(BOX_WT) / 1000 AS BOX_WT
             , SUM(BOX_WT_MIN) / 1000 AS BOX_WT_MIN
             , SUM(BOX_WT_MAX) / 1000 AS BOX_WT_MAX
          FROM (
                SELECT BOX_WT, BOX_WT_MIN, BOX_WT_MAX
                  FROM BOX_TYPES
                 WHERE DOMAIN_ID = :domainId
                   AND BOX_TYPE_CD = :boxTypeCd
                   AND 'BOX' = :boxType
                UNION ALL
                SELECT 0, 0, 0 FROM DUAL
               )
       ) Z
      , (SELECT DISTINCT SHOP_CD, SHOP_NM, ORDER_TYPE, BOX_TYPE_CD, BOX_ID, CLASS_CD, BOX_CLASS_CD
           FROM JOB_INSTANCES
         WHERE DOMAIN_ID = :domainId
           AND BATCH_ID = :batchId
           AND ORDER_NO = :orderNo ) K
