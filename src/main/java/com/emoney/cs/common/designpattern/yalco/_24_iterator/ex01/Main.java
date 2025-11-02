package com.emoney.cs.common.designpattern.yalco._24_iterator.ex01;

// Client
public class Main {
    public static void main(String[] args) {
        MyList list = new MyList(3);
        list.add("A");
        list.add("B");
        list.add("C");

        MyIterator iterator = list.createIterator();
        while (iterator.hasNext()) {
            System.out.println(iterator.next());
        }
    }
}