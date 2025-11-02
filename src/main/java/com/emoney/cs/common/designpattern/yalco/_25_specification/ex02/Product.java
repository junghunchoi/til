package com.emoney.cs.common.designpattern.yalco._25_specification.ex02;

public class Product {
    private String name;
    private String category;
    private int price;
    private int stock;

    public Product(String name, String category, int price, int stock) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }
}