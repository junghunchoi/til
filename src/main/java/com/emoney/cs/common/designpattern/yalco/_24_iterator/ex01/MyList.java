package com.emoney.cs.common.designpattern.yalco._24_iterator.ex01;

// Concrete Aggregate
class MyList implements Collection {
    private Object[] items;
    private int last = 0;

    public MyList(int size) {
        items = new Object[size];
    }

    public void add(Object item) {
        if (last < items.length) {
            items[last] = item;
            last++;
        }
    }

    public Object get(int index) {
        return items[index];
    }

    public int size() {
        return last;
    }

    @Override
    public MyIterator createIterator() {
        return new MyListIterator(this);
    }
}