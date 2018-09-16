package com.concurrent.example;

import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * P737
 * 银行出纳员仿真（并没有研究原理？？？？？？）
 * 应用情况：对象随机地出现，并且要求由数量有限的服务器提供随机数量的服务时间。
 */
//Read-only objects don't require synchronization
class Customer {
    private final int serviceTime;
    public Customer(int tm) { serviceTime = tm; }
    public int getServiceTime() { return serviceTime; }

    @Override
    public String toString() {
        return "[" + serviceTime + "]";
    }
}

class CustomerLine extends ArrayBlockingQueue<Customer> {
    public CustomerLine(int maxLineSize) {
        super(maxLineSize);
    }

    @Override
    public String toString() {
        if (this.size() == 0) {
            return "[Empty]";
        }
        StringBuilder result = new StringBuilder();
        for (Customer customer : this) {
            result.append(customer);
        }
        return result.toString();
    }
}

//Randomly add customers to a queue
class CustomerGenerator implements Runnable {
    private CustomerLine customers;
    private static Random rand = new Random(47);
    public CustomerGenerator(CustomerLine cq) {
        customers = cq;
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                TimeUnit.MILLISECONDS.sleep(rand.nextInt(300));
                customers.put(new Customer(rand.nextInt(1000)));
            }
        } catch (InterruptedException e) {
            System.out.println("CustomerGenerator interrupted");
        }
        System.out.println("CustomerGenerator terminating");
    }
}

class Teller implements Runnable, Comparable<Teller> {
    private static int counter = 0;
    private static int id = counter ++;
    //Customers served during this shift;
    private int customersServed = 0;
    private CustomerLine customers;
    private boolean servingCustomerLine = true;
    public Teller(CustomerLine cq) { customers = cq; }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                Customer customer = customers.take();
                TimeUnit.MILLISECONDS.sleep(customer.getServiceTime());
                synchronized (this) {
                    customersServed ++;
                    while (!servingCustomerLine) {
                        wait();
                    }
                }
            }
        } catch (InterruptedException e) {
            System.out.println(this + "interrupted");
        }
        System.out.println(this + "terminating");
    }

    public synchronized void doSomethingElse() {
        customersServed = 0;
        servingCustomerLine = false;
    }

    public synchronized void serveCustomerLine() {
        assert !servingCustomerLine:"already serving: " + this;
        servingCustomerLine = true;
        notifyAll();
    }

    @Override
    public String toString() {
        return "Teller " + id + " ";
    }

    public String shortString() {
        return "T" + id;
    }

    //used by priority queue
    public synchronized int compareTo(Teller other) {
        return customersServed < other.customersServed ? -1 :
                (customersServed == other.customersServed ? 0 : 1);
    }
}

class TellerManager implements Runnable {
    private ExecutorService exec;
    private CustomerLine customers;
    private PriorityQueue<Teller> workingTellers = new PriorityQueue<Teller>();
    private Queue<Teller> tellersDoingOtherThings = new LinkedList<Teller>();
    private int adjustmentPeriod;
    private static Random rand  = new Random(47);
    public TellerManager(ExecutorService e,
                         CustomerLine customers, int adjustmentPeriod) {
        exec = e;
        this.customers = customers;
        this.adjustmentPeriod = adjustmentPeriod;
        //Start with a single teller
        Teller teller = new Teller(customers);
        exec.execute(teller);
        workingTellers.add(teller);
    }
    public void adjustTellerNumber() {
        //This is actually a control system. By adjusting
        //the numbers, you can reveal stability issues in
        //the control mechanism
        //If line is too long, add another teller;
        if (customers.size() / workingTellers.size() > 2) {
            //If tellers are on break or doing
            //another job, bring one back;
            if(tellersDoingOtherThings.size() > 0) {
                Teller teller = tellersDoingOtherThings.remove();
                teller.serveCustomerLine();
                workingTellers.offer(teller);
                return;
            }
            //Else create (hire) a new teller
            Teller teller = new Teller(customers);
            exec.execute(teller);
            workingTellers.add(teller);
            return;
        }
        //if line is short enough, remove a teller:
        if(workingTellers.size() > 1 && customers.size() / workingTellers.size() <2) {
            reassignOneTeller();
        }
        //if there is no line, we only need one teller
        if (customers.size() == 0) {
            while (workingTellers.size() > 1) {
                reassignOneTeller();
            }
        }
    }

    //Give a teller a different job or a break
    private void reassignOneTeller() {
        Teller teller = workingTellers.poll();
        teller.doSomethingElse();
        tellersDoingOtherThings.offer(teller);
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                TimeUnit.MILLISECONDS.sleep(adjustmentPeriod);
                adjustTellerNumber();
                System.out.println(customers + " { ");
                for (Teller teller : workingTellers) {
                    System.out.println(teller.shortString() + " ");
                }
                System.out.println(" } ");
            }
        } catch (InterruptedException e) {
            System.out.println(this + " interrupted");
        }
        System.out.println(this + "terminating");
    }

    @Override
    public String toString() {
        return "TellerManager ";
    }
}

public class BankTellerSimulation {
    static final int MAX_LINE_SIZE = 50;
    static final int ADJUSTMENT_PERIOD = 1000;

    public static void main(String[] args) throws Exception {
        ExecutorService exec = Executors.newCachedThreadPool();
        //If line is too long, customers will leave
        CustomerLine customers = new CustomerLine(MAX_LINE_SIZE);
        exec.execute(new CustomerGenerator(customers));
        //Manager will add and remove tellers as necessary
        exec.execute(new TellerManager(exec, customers, ADJUSTMENT_PERIOD));
        if(args.length > 0) { //Optonal argument
            TimeUnit.MILLISECONDS.sleep(new Integer(args[0]));
        } else {
            System.out.println("Press 'Enter' to quit");
            System.in.read();
        }
        exec.shutdownNow();
    }
}
