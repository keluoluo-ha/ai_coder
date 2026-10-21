package com.hhk.aicoder.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LRUCache {

    class DLinkedNode{
        int key;
        int value;
        DLinkedNode prev;
        DLinkedNode next;
        public DLinkedNode() {};
        public DLinkedNode(int _key,int _value){
            key=_key;
            value=_value;
        }
    }
    private Map<Integer,DLinkedNode> cache=new HashMap<Integer,DLinkedNode>();
    private int size;
    private int capacity;
    private DLinkedNode head,tail;

    public LRUCache(int capacity) {
        this.size=0;
        this.capacity=capacity;
        head=new DLinkedNode();
        tail=new DLinkedNode();

    }
    
    public int get(int key) {
        DLinkedNode dLinkedNode = cache.get(key);
        if(dLinkedNode==null){
            return -1;
        }
       moveTohead(dLinkedNode);

        return dLinkedNode.value;
    }

    private void moveTohead(DLinkedNode dLinkedNode) {
        removeNode(dLinkedNode);
        addTohead(dLinkedNode);

    }

    private void addTohead(DLinkedNode dLinkedNode) {
        dLinkedNode.prev=head;
        dLinkedNode.next=head.next;
        head.next.prev=dLinkedNode;
        head.next=dLinkedNode;
    }

    public void put(int key, int value) {
        DLinkedNode dLinkedNode = cache.get(key);
        if(dLinkedNode==null){
            DLinkedNode node = new DLinkedNode(key, value);
            cache.put(key,node);
            addTohead(node);
            ++size;
            if(size>capacity) {
                DLinkedNode tail = removeTail();
                cache.remove(tail);
                --size;
            }else {
                node.value=value;
                moveTohead(node);
            }
        }

    }

    private DLinkedNode removeTail() {
        DLinkedNode res = tail.prev;
        removeNode(res);
        return res;
    }

    private void removeNode(DLinkedNode node){
        node.prev.next=node.next;
        node.next.prev=node.prev;
    }
}