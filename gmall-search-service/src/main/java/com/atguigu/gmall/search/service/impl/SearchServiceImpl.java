package com.atguigu.gmall.search.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.atguigu.gmall.bean.PmsSearchParam;
import com.atguigu.gmall.bean.PmsSearchSkuInfo;
import com.atguigu.gmall.bean.PmsSkuAttrValue;
import com.atguigu.gmall.service.SearchService;
import io.searchbox.client.JestClient;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    JestClient jestClient;

    @Override
    public List<PmsSearchSkuInfo> list(PmsSearchParam pmsSearchParam) {




        String dslStr=getSearchDsl(pmsSearchParam);


        //用api执行复杂查询
        System.err.println(dslStr);


        List<PmsSearchSkuInfo> pmsSearchSkuInfos= new ArrayList<>();

        Search search = new Search.Builder(dslStr).addIndex("gmall0105").addType("PmsSkuInfo").build();

        SearchResult execute = null;
        try {
            execute = jestClient.execute(search);
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<SearchResult.Hit<PmsSearchSkuInfo, Void>> hits = execute.getHits(PmsSearchSkuInfo.class);

        for (SearchResult.Hit<PmsSearchSkuInfo, Void> hit : hits) {
            PmsSearchSkuInfo source = hit.source;


            //在这里添加高亮到结果中
            //没搜索关键字则不应有高亮
            Map<String, List<String>> highlight = hit.highlight;

            if(highlight!=null){
                String skuName = highlight.get("skuName").get(0);//有可能出现空指针
                source.setSkuName(skuName);
            }


            pmsSearchSkuInfos.add(source);
        }

        System.out.println(pmsSearchSkuInfos.size());

        return pmsSearchSkuInfos;
    }

    //将前端发送过来的内容转换成 dsl语句
    private String getSearchDsl(PmsSearchParam pmsSearchParam) {
        String keyword = pmsSearchParam.getKeyword();

        String[] skuAttrValueList = pmsSearchParam.getValueId();

        String catalog3Id = pmsSearchParam.getCatalog3Id();

//jest的dsl工具//代替复杂的json字符串
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        //bool
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        //filter

        if(StringUtils.isNotBlank(catalog3Id)){
            TermQueryBuilder termQueryBuilder = new TermQueryBuilder("catalog3Id",catalog3Id);
            boolQueryBuilder.filter(termQueryBuilder);
        }

        if(skuAttrValueList!=null)
        {
            for (String pmsSkuAttrValue : skuAttrValueList) {
                TermQueryBuilder termQueryBuilder = new TermQueryBuilder("skuAttrValueList.valueId",pmsSkuAttrValue);
                boolQueryBuilder.filter(termQueryBuilder);
            }
        }



        //TermQueryBuilder termQueryBuilder1 = new TermQueryBuilder("","");
        //boolQueryBuilder.filter(termQueryBuilder1);
        //TermsQueryBuilder termsQueryBuilder = new TermsQueryBuilder("","");
        //boolQueryBuilder.filter(termsQueryBuilder);
        //must

        if(StringUtils.isNotBlank(keyword)){
            MatchQueryBuilder matchQueryBuilder =new MatchQueryBuilder("skuName",keyword);
            boolQueryBuilder.must(matchQueryBuilder);
        }


        //query
        searchSourceBuilder.query(boolQueryBuilder);
        //from
        searchSourceBuilder.from(0);
        //size
        searchSourceBuilder.size(1000);
        //highlight高亮

        HighlightBuilder highlightBuilder=new HighlightBuilder();
        highlightBuilder.preTags("<span style='color:red'>");
        highlightBuilder.field("skuName");
        highlightBuilder.postTags("</span>");
        searchSourceBuilder.highlight(highlightBuilder);

        //排序sort

        searchSourceBuilder.sort("id", SortOrder.DESC);


        //aggs聚合
        TermsBuilder groupby_attr = AggregationBuilders.terms("groupby_attr").field("skuAttrValueList.valueId");
        searchSourceBuilder.aggregation(groupby_attr);


        return searchSourceBuilder.toString();
    }
}
