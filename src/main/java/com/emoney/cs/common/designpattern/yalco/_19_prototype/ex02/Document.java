package com.emoney.cs.common.designpattern.yalco._19_prototype.ex02;

// Document interface extending Prototype
interface Document extends Prototype {
    void setContent(String content);
    String getContent();
}