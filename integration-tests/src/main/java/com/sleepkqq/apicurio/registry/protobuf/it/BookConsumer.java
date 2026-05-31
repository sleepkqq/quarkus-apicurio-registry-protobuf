package com.sleepkqq.apicurio.registry.protobuf.it;

import java.util.concurrent.LinkedBlockingQueue;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import com.example.catalog.Book;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BookConsumer {

    public static final LinkedBlockingQueue<Book> received = new LinkedBlockingQueue<>();

    @Incoming("books-in")
    public void consume(Book book) {
        received.offer(book);
    }
}
