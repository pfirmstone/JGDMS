package org.apache.river.security.concurrent;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.NoSuchElementException;

class WeakGroup {

    private final ReferenceQueue rq = new ReferenceQueue();
    private final Node head;
    private final Node tail;

    WeakGroup() {
        super();
        head = Node.createEmptyList();
        tail = head.getNext();
    }

    void add(Object obj) {
        if (obj == null) {
            throw new NullPointerException();
        }
        processQueue();
        Node newNode = new Node(obj, rq);
        newNode.insertAfter(head);
    }

    Iterator iterator() {
        processQueue();
        return new Iterator() {

            private Node curNode = head.getNext();
            private Object nextObj = getNext();

            public Object next() {
                if (nextObj == null) {
                    throw new NoSuchElementException();
                }
                Object obj = nextObj;
                nextObj = getNext();
                return obj;
            }

            public boolean hasNext() {
                return nextObj != null;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

            private Object getNext() {
                while (curNode != tail) {
                    Object obj = curNode.get();
                    if (obj != null) {
                        curNode = curNode.getNext();
                        return obj;
                    } else {
                        curNode.enqueue();
                        curNode = curNode.getNext();
                    }
                }
                return null;
            }
        };
    }

    private void processQueue() {
        Node n;
        while ((n = (Node) rq.poll()) != null) {
            n.remove();
        }
    }

    private static class Node extends WeakReference {

        private Node next;
        private Node prev;

        static Node createEmptyList() {
            Node head = new Node(null);
            Node tail = new Node(null);
            head.next = tail;
            tail.prev = head;
            return head;
        }

        private Node(Object obj) {
            super(obj);
        }

        Node(Object obj, ReferenceQueue rq) {
            super(obj, rq);
        }

        void insertAfter(Node pred) {
            Node succ = pred.next;
            next = succ;
            prev = pred;
            pred.next = this;
            succ.prev = this;
        }

        void remove() {
            prev.next = next;
            next.prev = prev;
        }

        Node getNext() {
            return next;
        }
    }
}
