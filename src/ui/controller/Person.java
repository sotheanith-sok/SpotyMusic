package ui.controller;
public class Person{
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

   public String getAge() {
      return age;
   }

   public String getDate() {
      return date;
   }
   @Override
   public String toString(){
      return "Name: "+ name + ", Age: "+ age+ ", Data:"+date;
   }

   public void setName(String name) {
      this.name = name;
   }

   public void setAge(String age) {
      this.age = age;
   }

   public void setDate(String date) {
      this.date = date;
   }
}