package com.emoney.cs.common.designpattern.yalco._24_iterator.ex01;

// Aggregate Interface
interface Collection {
    MyIterator createIterator();
}