package com.selsup.sels_api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Класс для работы с API Честного знака.
 * Этот класс обеспечивает потокобезопасное выполнение HTTP запросов с ограничением на количество запросов в заданный интервал времени.
 */
public class SelsupApi {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore semaphore;

    /**
     * Конструктор класса SelsupApi.
     *
     * @param timeUnit     единица времени, в которой измеряется интервал для ограничения количества запросов.
     * @param requestLimit максимальное количество запросов в указанный интервал времени.
     */
    public SelsupApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.semaphore = new Semaphore(requestLimit);

        // Запуск потока, который будет сбрасывать семафор через указанный интервал времени
        new Thread(() -> {
            while (true) {
                try {
                    timeUnit.sleep(1);
                    semaphore.release(requestLimit - semaphore.availablePermits());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    /**
     * Метод для создания документа для ввода в оборот товара, произведенного в РФ.
     *
     * @param document  объект документа, который нужно отправить в API.
     * @param signature строка подписи для авторизации.
     * @return ответ API в виде строки.
     * @throws IOException          при ошибках сериализации или отправки запроса.
     * @throws InterruptedException если поток был прерван во время выполнения запроса.
     */
    public String createDocument(Document document, String signature) throws IOException, InterruptedException {
        // Ожидание доступности слота в семафоре
        semaphore.acquire();

        // Сериализация документа в JSON
        String requestBody = objectMapper.writeValueAsString(document);

        // Формирование запроса
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // Выполнение запроса
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Возврат ответа
        return response.body();
    }

    /**
     * Внутренний класс для представления документа.
     */
    @Data
    @NoArgsConstructor
    public static class Document {
        private String participantInn;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn2;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private Product[] products;
        private String regDate;
        private String regNumber;

        /**
         * Внутренний класс для представления продукта.
         */
        @Data
        @NoArgsConstructor
        public static class Product {
            private String certificateDocument;
            private String certificateDocumentDate;
            private String certificateDocumentNumber;
            private String ownerInn;
            private String producerInn;
            private String productionDate;
            private String tnvedCode;
            private String uitCode;
            private String uituCode;
        }
    }
}

