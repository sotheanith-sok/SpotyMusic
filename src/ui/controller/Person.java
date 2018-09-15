package ui.controller;

public class Person {
    private String name, age, date;

    public Person(String name) {
        this.name = name;
    }

    public Person(String name, String age, String date) {
        this.name = name;
        this.age = age;
        this.date = date;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        this.age = age;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "Name: " + name + ", Age: " + age + ", Data:" + date;
    }
}