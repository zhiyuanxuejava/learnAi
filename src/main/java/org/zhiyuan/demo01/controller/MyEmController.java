package org.zhiyuan.demo01.controller;

import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@RestController
public class MyEmController {


    private static OllamaEmbeddingModel embeddingModel;

    // 注入向量数据库
    private static VectorStore vectorStore;

    public MyEmController(OllamaEmbeddingModel embeddingModel,
                          VectorStore vectorStore
    ) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;

    }


    //将数据添加到向量数据库中
    @RequestMapping("/addDoc")
    public String addDoc(){
        List<Document> docs = Arrays.asList(
                new Document("java书籍"),
                new Document("学习java"),
                new Document("Java岛"),
                new Document("开发语言用的Java"),
                new Document("用苹果手机"),
                new Document( "吃苹果")
        );
        vectorStore.add(docs);
        return "ok";
    }

    /**
     * topK(2)：选出最接近的两个候选
     * score：越大越相似
     * distance / vector_score：越小越相似
     * @param query
     * @return
     */
    @RequestMapping("/query")
    public String query(String  query){
        //找回 TOP K 个最相似的文档
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest
                        .builder()
                        .query(query)
                        .topK(2)
                        .build()
        );

        //排序输出
        List<Document> documentList = docs.stream()
                .sorted(Comparator.comparing(
                        Document::getScore,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
        System.out.println(documentList);
        return documentList.toString();
    }

    @RequestMapping("/em")
    public String em() {
        float[] textVector1 = embeddingModel.embed("测试文本");
        float[] textVector2 = embeddingModel.embed("java书籍");
        float[] textVector3 = embeddingModel.embed("学习java");
        float[] textVector4 = embeddingModel.embed("用苹果手机");
        float[] textVector5 = embeddingModel.embed("吃苹果");

        System.out.println("向量维度：" + textVector1.length);

        printSimilarity("java书籍", textVector2, "学习java", textVector3);
        printSimilarity("用苹果手机", textVector4, "吃苹果", textVector5);
        printSimilarity("java书籍", textVector2, "吃苹果", textVector5);
        printSimilarity("测试文本", textVector1, "java书籍", textVector2);

        return "ok";
    }

    private void printSimilarity(String textA, float[] vectorA, String textB, float[] vectorB) {
        double cosine = cosineSimilarity(vectorA, vectorB);
        double euclidean = euclideanDistance(vectorA, vectorB);

        System.out.println("文本A：" + textA);
        System.out.println("文本B：" + textB);
        System.out.println("余弦相似度：" + cosine);
        System.out.println("欧式距离：" + euclidean);
        System.out.println("----------------------------");
    }

    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("向量不能为空，并且维度必须一致");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double euclideanDistance(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("向量不能为空，并且维度必须一致");
        }

        double sum = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            double diff = vectorA[i] - vectorB[i];
            sum += diff * diff;
        }

        return Math.sqrt(sum);
    }
}

