package com.leyou.search.pojo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;
import java.util.Map;
import java.util.Set;

@Data
@Document(indexName = "goods", type = "docs", shards = 1, replicas = 0)
public class Goods {
    @Id
    private Long id; // spuId; 以spu为存储单位

    @Field(type = FieldType.Text,analyzer = "ik_max_word")
    private String all; // 所有需要被搜索的资源，包含标题，分类，甚至品牌;

    @Field(type = FieldType.Keyword, index = false)
    private String subTitle; // 卖点; 不做分词 不做搜索，只是展示用；

    private Long brandId; // 品牌id; 品牌过滤时用到;
    private Long cid1; // 1级分类id; 商品分类时用到;
    private Long cid2; // 2级分类id;
    private Long cid3; // 3级分类id;
    private Date createTime; // spu创建时间; 新品过滤;

    private Set<Long> price; // 价格; 各sku的价格;

    @Field(type = FieldType.Keyword, index = false)
    private String skus; // sku信息的json结构; 用于展示;
    private Map<String, Object> specs; // 可搜索的规格参数，key是参数名，值是参数值; 规格参数过滤;
}
