package com.emoney.cs.common.designpattern.yalco._15_builder.ex01;

public class Main {
    public static void main(String[] args) {
        Pizza myPizza = new Pizza.PizzaBuilder()
                .dough("Thin Crust")
                .sauce("Tomato")
                .topping("Cheese")
                .build();

        System.out.println(myPizza);

        String orderType = "Veggie";

        Pizza.PizzaBuilder pizzaBuilder
                = new Pizza.PizzaBuilder().dough("Regular");

        pizzaBuilder.sauce("Pesto");

        if (orderType.equals("Veggie")) {
            pizzaBuilder.topping("Vegetables");
        } else {
            pizzaBuilder.topping("Pepperoni");
        }

        Pizza customPizza = pizzaBuilder.build();
        System.out.println(customPizza);
    }
}