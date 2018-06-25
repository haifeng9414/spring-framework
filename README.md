# 个人总结:

- [容器的实现]
    - 加载Bean配置信息(#加载Bean配置信息)
    - 从容器获取bean

## 加载Bean配置信息

1. 加载Bean的配置信息有很多中方法，如从XML解析、根据注解解析等，下面主要介绍根据XML文件解析Bean配置信息。
Spring中从XML文件解析Bean配置的信息的默认实现是[XmlBeanDefinitionReader]，该类的构造函数接收[BeanDefinitionRegistry]类为参数，
[BeanDefinitionRegistry]用于Bean信息的增删改查，默认实现是[DefaultListableBeanFactory]，最简单的Spring容器测试代码如下:
```java
DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
new XmlBeanDefinitionReader(factory).loadBeanDefinitions(PATH_TO_XML);
```

[XmlBeanDefinitionReader]: aaa
[BeanDefinitionRegistry]: aaa
[DefaultListableBeanFactory]: aaa

## 从容器获取bean

1. 