[FactoryBean]是个接口，实现类本身不作为一个普通bean使用，而是用于创建bean，接口定义如下：
```java
public interface FactoryBean<T> {
	@Nullable
	T getObject() throws Exception;

	@Nullable
	Class<?> getObjectType();

	default boolean isSingleton() {
		return true;
	}
}
```
`getObject()`方法返回[FactoryBean]所创建的bean，[FactoryBean]在容器中和普通单例bean一样保存在容器中，在需要获取其创建的bean时会先获取到[FactoryBean]，再调用其`getObject()`方法返回bean，[FactoryBean]使用例子：
```java
public class MyBean {
	private String id;
	private String name;

	public MyBean(String id, String name) {
		this.id = id;
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}

public class MyBeanFactoryBean implements FactoryBean<MyBean> {
	@Override
	public Class<?> getObjectType() {
		return MyBeanA.class;
	}

	@Override
	public MyBean getObject() throws Exception {
		return new MyBean("1", "2");
	}

	@Override
	public boolean isSingleton() {
		return true;
	}
}
```
使用xml定义bean：
```xml
<bean id="myBean" class="org.springframework.tests.sample.beans.MyBeanFactoryBean"/>
```
测试：
```java
public void myTest() {
    ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext(classPathResource("-application-context.xml").getPath(), getClass());
    MyBean myBean1 = applicationContext.getBean("myBean", MyBean.class);
    System.out.println(myBean1.getId());
    System.out.println(myBean1.getClass());
    MyBean myBean2 = applicationContext.getBean("myBean", MyBean.class);
    System.out.println(myBean1.getClass().equals(myBean2.getClass()));
}

/* 
输出:
1
class org.springframework.tests.sample.beans.MyBean
true
*/
```
上面的测试可以发现，xml中定义的class为[FactoryBean]的实现类[MyBeanFactoryBean]，当从容器获取myBean时，返回的是[MyBeanFactoryBean]的`getObject()`方法的返回值，由于[MyBeanFactoryBean]的`isSingleton()`方法返回true，所以多次获取myBean返回的是同一个[MyBean]对象

[FactoryBean]: aaa
[MyBeanFactoryBean]: aaa
[MyBean]: aaa