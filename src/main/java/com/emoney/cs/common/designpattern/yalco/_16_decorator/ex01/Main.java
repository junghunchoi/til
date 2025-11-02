package com.emoney.cs.common.designpattern.yalco._16_decorator.ex01;

// Client code
public class Main {
    public static void main(String[] args) {
        // Simple coffee
        Coffee simpleCoffee = new SimpleCoffee();
        System.out.println(
                simpleCoffee.getDescription() + " $" + simpleCoffee.getCost()
        );

        // Coffee with milk
        Coffee milkCoffee = new MilkDecorator(new SimpleCoffee());
        System.out.println(
                milkCoffee.getDescription() + " $" + milkCoffee.getCost()
        );

        // Coffee with milk and sugar
        Coffee milkAndSugarCoffee = new SugarDecorator(
                new MilkDecorator(
                        new SimpleCoffee()
                )
        );
        System.out.println(
                milkAndSugarCoffee.getDescription() +
                        " $" + milkAndSugarCoffee.getCost()
        );
    }
}