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

```
什么情况下会导致调用getObjectFromFactoryBean方法时containsSingleton返回false呢，一种情况时，如果存在两个
FactoryBean，分别为FactoryBeanA和FactoryBeanB，分别负责创建BeanA和BeanB，假设创建BeanA需要BeanB，创建BeanB需要beanA，
也就是循环引用，这种情况下xml需要如下配置：

<bean id="myBeanForFactoryBeanA" class="org.springframework.tests.sample.beans.MyFactoryBeanForFactoryBeanA">
    <property name="myBeanForFactoryBeanB" ref="myBeanForFactoryBeanB"/>
</bean>

<bean id="myBeanForFactoryBeanB" class="org.springframework.tests.sample.beans.MyFactoryBeanForFactoryBeanB">
    <property name="myBeanForFactoryBeanA" ref="myBeanForFactoryBeanA"/>
</bean>

查看AbstractBeanFactory的doGetBean方法中调用的getSingleton方法可知，bean只有在完全创建完成后才会被添加到singletonObjects中，
当第一次获取myBeanForFactoryBeanA，也就是创建获取myBeanForFactoryBeanA时，会填充其myBeanForFactoryBeanB属性为bean：myBeanForFactoryBeanB，
这就需要创建myBeanForFactoryBeanB，创建myBeanForFactoryBeanB时又会填充myBeanForFactoryBeanA，此时myBeanForFactoryBeanA在spring中的保存形式是
以getObject方法返回值为MyFactoryBeanForFactoryBeanA的ObjectFactory实例保存在singletonFactories中，为myBeanForFactoryBeanB填充myBeanForFactoryBeanA
会使得以MyFactoryBeanForFactoryBeanA、myBeanForFactoryBeanA为参数调用这里的getObjectFromFactoryBean方法，但是myBeanForFactoryBeanA还在创建过程中，
使得代码走向下面的else语句直接返回MyFactoryBeanForFactoryBeanA的getObject方法，此时MyFactoryBeanForFactoryBeanA中的myBeanForFactoryBeanB为null，所以创建
出来的myBeanForFactoryBeanA的myBeanForFactoryBeanB属性为空，并且这个myBeanForFactoryBeanA会作为myBeanForFactoryBeanB的myBeanForFactoryBeanA属性，
完成了myBeanForFactoryBeanB的创建后回到了最初myBeanForFactoryBeanA的创建，填充了一个myBeanForFactoryBeanB实例，并且其myBeanForFactoryBeanA已经被设置上了，
此时结束myBeanForFactoryBeanA的创建，并添加myBeanForFactoryBeanA到singletonObjects，之后再次执行这里的getObjectFromFactoryBean方法，导致又创建了一个
myBeanForFactoryBeanA，这违背了循环引用的初衷
```

[FactoryBean]: aaa
[MyBeanFactoryBean]: aaa
[MyBean]: aaa