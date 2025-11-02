package com.emoney.cs.common.designpattern.yalco._24_iterator.ex01;

// ConcreteIterator
class MyListIterator implements MyIterator {
    private MyList list;
    private int index;

    public MyListIterator(MyList list) {
        this.list = list;
        this.index = 0;
    }

    @Override
    public boolean hasNext() {
        return index < list.size();
    }

    @Override
    public Object next() {
        if (this.hasNext()) {
            return list.get(index++);
        }
        return null;
    }
}