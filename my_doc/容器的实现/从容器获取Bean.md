## 从容器获取Bean

在初始化完容器后，[BeanFactory]也就初始化完了，容器中Bean都是从[BeanFactory]中获取的，同样以[ClassPathXmlApplicationContext]为例，[ClassPathXmlApplicationContext]的[BeanFactory]实现是[DefaultListableBeanFactory]，[DefaultListableBeanFactory]继承结构图如下：
![DefaultListableBeanFactory继承结构图](../../img/DefaultListableBeanFactory.png)

获取Bean：
```java
ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext(classPathResource("-application-context.xml").getPath(), getClass());
MyBeanA myBeanA = applicationContext.getBean("myBeanA", MyBeanA.class);
```
getBean方法代码：
```java
public <T> T getBean(String name, @Nullable Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
	return getBeanFactory().getBean(name, requiredType);
}
```

容器获取Bean的过程完全委托给了[BeanFactory]，默认实现就是[DefaultListableBeanFactory]，[DefaultListableBeanFactory]实现了很多接口，每个接口都赋予了[DefaultListableBeanFactory]不同的职能：
1. [AliasRegistry]接口包含了别名管理的相关方法
2. [SimpleAliasRegistry]类利用Map实现了[AliasRegistry]接口并提供了循环别名检查
3. [BeanDefinitionRegistry]接口定义了[BeanDefinition]的管理方法，包括`beanName`与[BeanDefinition]关联关系的增删改查
4. [BeanFactory]接口定义了访问Bean的相关操作，如`getBean()`，`isSingleton()`，`getType()`等方法
5. [SingletonBeanRegistry]接口定义了访问和注册单例Bean的相关方法，如`registerSingleton()`，`getSingleton()`
6. [DefaultSingletonBeanRegistry]类实现了基本的单例的注册功能，相当于是一个单例Bean的[BeanFactory]，并为单例Bean的循环引用提供了支持，该类的方法在获取单例Bean的过程中起了决定性作用，下面会具体分析
7. [FactoryBeanRegistrySupport]类增加了对[FactoryBean]的支持，能够从[FactoryBean]中获取Bean(主要方法是`getObjectFromFactoryBean`)
8. [HierarchicalBeanFactory]接口在[BeanFactory]基础上添加了两个方法`getParentBeanFactory`和`containsLocalBean`，增加了[BeanFactory]的继承功能
9. [ConfigurableBeanFactory]添加了配置[BeanFactory]的方法，即若干个setter方法，该接口主要在Spring内部配置[BeanFactory]时使用，如设置[BeanFactory]的[BeanExpressionResolver]、[ConversionService]、[PropertyEditorRegistrar]等
10. [AbstractBeanFactory]类实现了[ConfigurableBeanFactory]接口的全部方法并继承了[FactoryBeanRegistrySupport]类，用模版方法模式定义好了获取bean的基本逻辑，提供一些抽象方法供子类实现(主要是`getBeanDefinition`和`createBean`)，从容器获取Bean的主要过程就在该类的`doGetBean`方法
11. [AutowireCapableBeanFactory]接口增加了创建Bean、自动注入、初始化以及调用[BeanPostProcessor]等相关方法
12. [AbstractAutowireCapableBeanFactory]类继承[AbstractBeanFactory]并实现了[AutowireCapableBeanFactory]接口，bean的属性和构造函数参数自动注入都是该类实现的
13. [ListableBeanFactory]接口定义了获取Bean配置清单的相关方法，如获取所有的BeanName，根据类型、注解获取Bean列表等
14. [ConfigurableListableBeanFactory]接口添加了分析和修改[BeanDefinition]、单例预实例化等方法，该接口主要在Spring内部使用
15. [DefaultListableBeanFactory]综合上面所有功能，并实现了[ListableBeanFactory]和[BeanDefinitionRegistry]接口

上面相关接口和抽象类的具体实现可以看代码中的注释，下面着重介绍Bean的获取过程，Bean的获取入口是[DefaultListableBeanFactory]的`getBean()`方法，代码：
```java
public <T> T getBean(String name, @Nullable Class<T> requiredType) throws BeansException {
	return doGetBean(name, requiredType, null, false);
}

// 该方法实现了从容器获取Bean的基本过程，用模版方法模式调用了几个抽象方法实现创建Bean的过程
protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,
        @Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {

    // 提取bean的名字，如果是别名则返回的是真正的bean的名字，如果是FactoryBean的名字则去掉前面的BeanFactory.FACTORY_BEAN_PREFIX
    final String beanName = transformedBeanName(name);
    Object bean;

    // Eagerly check singleton cache for manually registered singletons.
    // 从缓存中获取单例bean，如果存在则返回
    Object sharedInstance = getSingleton(beanName);
    if (sharedInstance != null && args == null) {
        if (logger.isDebugEnabled()) {
            if (isSingletonCurrentlyInCreation(beanName)) {
                logger.debug("Returning eagerly cached instance of singleton bean '" + beanName +
                        "' that is not fully initialized yet - a consequence of a circular reference");
            }
            else {
                logger.debug("Returning cached instance of singleton bean '" + beanName + "'");
            }
        }
        // 如果sharedInstance是一个普通bean的话直接返回，如果是FactoryBean的话返回getObject方法的返回值
        bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
    }

    else {
        // Fail if we're already creating this bean instance:
        // We're assumably within a circular reference.
        // 对于以prototype为scope的bean，如果在当前加载过程中发现当前bean已经处于创建过程，则抛出异常，防止循环引用
        if (isPrototypeCurrentlyInCreation(beanName)) {
            throw new BeanCurrentlyInCreationException(beanName);
        }

        // Check if bean definition exists in this factory.
        // 判断是否存在父BeanFactory，如果存在并且当前BeanFactory的BeanDefinition中不包含正在创建的bean对应的BeanDefinition则从父BeanFactory获取bean
        BeanFactory parentBeanFactory = getParentBeanFactory();
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // Not found -> check parent.
            // 还原beanName，如果是FactoryBean则为&beanName
            String nameToLookup = originalBeanName(name);
            if (parentBeanFactory instanceof AbstractBeanFactory) {
                return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
                        nameToLookup, requiredType, args, typeCheckOnly);
            }
            else if (args != null) {
                // Delegation to parent with explicit args.
                return (T) parentBeanFactory.getBean(nameToLookup, args);
            }
            else {
                // No args -> delegate to standard getBean method.
                return parentBeanFactory.getBean(nameToLookup, requiredType);
            }
        }

        // 如果不仅仅是类型检查则将当前bean置成已创建状态，即保存到alreadyCreated中
        if (!typeCheckOnly) {
            markBeanAsCreated(beanName);
        }

        try {
            // 获取bean的BeanDefinition
            final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
            // 如果BeanDefinition声明了bean是abstract的则报错
            checkMergedBeanDefinition(mbd, beanName, args);

            // Guarantee initialization of beans that the current bean depends on.
            // 确保当前bean声明的dependOn的bean已经被初始化了，这里的dependOn不同于bean的属性依赖，而是用户自己声明的
            // 如BeanA可以声明dependOn BeanB，即使两个bean没有关系，dependOn用于确保创建bean之前已经创建了某些bean，
            // 并且dependOn不可以循环依赖
            String[] dependsOn = mbd.getDependsOn();
            if (dependsOn != null) {
                // 如果存在dependsOn则遍历并创建
                for (String dep : dependsOn) {
                    // 判断dep是否依赖beanName，如果是则为循环依赖，报错
                    if (isDependent(beanName, dep)) {
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                "Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
                    }
                    // 保存beanName依赖dep的依赖关系
                    registerDependentBean(dep, beanName);
                    try {
                        // 创建bean，如果不存在则抛出异常
                        getBean(dep);
                    }
                    catch (NoSuchBeanDefinitionException ex) {
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                "'" + beanName + "' depends on missing bean '" + dep + "'", ex);
                    }
                }
            }

            // Create bean instance.
            // 如果bean是单例的
            if (mbd.isSingleton()) {
                // 单例bean是支持循环引用的，处理过程是，这里先创建一个ObjectFactory（下面的lambda就是ObjectFactory实例），在getSingleton中将会调用这里的匿名ObjectFactory的
                // createBean方法，而createBean方法会调用doCreateBean方法创建bean，doCreateBean在创建bean时解析构造函数或者工厂方法
                // 创建一个没有注入任何属性的简单bean，在创建完成后执行addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
                // 将能够返回刚创建的bean的匿名ObjectFactory添加到内存中，getEarlyBeanReference遍历所有的SmartInstantiationAwareBeanPostProcessor对bean做一些特殊处理，AOP就是通过
                // SmartInstantiationAwareBeanPostProcessor实现的，如果不存在SmartInstantiationAwareBeanPostProcessor则直接返回简单bean。在将ObjectFactory添加到内存中后将继续该bean的创建，
                // 首先是populateBean方法，该方法将填充bean的属性，而填充属性就可能导致循环引用，假设当前正在创建的bean是beanA，而beanA有属性beanB，populateBean就将填充beanB到beanA，
                // 由于之前没有创建过beanB，所以填充之前将会创建一个beanB，如果beanB也有属性beanA，则存在循环引用，在创建beanB的时候将会尝试获取beanA，获取方式就是执行getBean(beanA)，调用getBean(beanA)使得
                // getSingleton(beanA)方法被调用，该方法将会获取到之前添加到内存的匿名beanFactory并通过该beanFactory的getBean方法获取到刚创建完成正在
                // 填充属性的beanA，在获取到beanA后beanB就能够顺利初始化了，之后返回beanB并继续beanA的创建，以上就是解决循环引用的过程
                sharedInstance = getSingleton(beanName, () -> {
                    try {
                        /*
                        该方法创建bean，创建过程中涉及到了多个接口方法调用，顺序和用途如下:
                        1.如果当前BeanFactory存在InstantiationAwareBeanPostProcessor则调用该InstantiationAwareBeanPostProcessor的postProcessBeforeInstantiation方法，
                        如果方法返回了一个对象则以该对象作为bean返回并在返回前遍历所有的BeanPostProcessor调用postProcessAfterInitialization方法(该方法的设计目的是在bean及其属性初始化完毕后调用，
                        由于InstantiationAwareBeanPostProcessor的postProcessBeforeInstantiation返回的对象将作为最终的bean，所以需要在返回前调用该方法)对bean进行处理，如果遍历过程中某个
                        BeanPostProcessor的postProcessAfterInitialization返回null则结束遍历，否则以返回的对象为bean并继续遍历
                        2.如果正在创建的bean有Supplier则以Supplier提供的对象为bean，否则如果存在工厂方法则以工厂方法的返回结果做为bean，否则如果存在构造函数参数则根据构造函数参数，自动解析所有构造函数，
                        或在SmartInstantiationAwareBeanPostProcessor接口的返回的构造函数列表中，获取满足构造函数参数的构造函数并创建bean，否则以默认构造函数创建bean
                        3.获取所有的MergedBeanDefinitionPostProcessor调用postProcessMergedBeanDefinition对正在创建的bean的BeanDefinition就行处理
                        4.根据创建出来的bean创建ObjectFactory并将ObjectFactory添加到singletonFactories以支持循环依赖
                        5.开始设置bean的属性，在此之前再次获取所有的InstantiationAwareBeanPostProcessor，执行postProcessAfterInstantiation方法，目的是在设置属性之前对bean做定制，
                        遍历过程中如果某个InstantiationAwareBeanPostProcessor的postProcessAfterInstantiation返回false则结束遍历
                        6.根据autowire的设置，如果是byType或byName则用相应的策略填充属性，对于循环依赖的属性解决方法在上面的注释已经说了
                        7.如果bean实现了BeanNameAware、BeanClassLoaderAware或者BeanFactoryAware则调用相应的接口方法
                        8.遍历所有的BeanPostProcessor调用postProcessBeforeInitialization方法，此时bean的依赖都注入了
                        9.如果bean实现了InitializingBean接口则调用afterPropertiesSet方法，如果bean存在init-method则调用自定义的方法
                        10.遍历所有的BeanPostProcessor调用postProcessAfterInitialization方法
                        11.注册当前bean的DisposableBeanAdapter对象用于在销毁bean时遍历DestructionAwareBeanPostProcessor调用回调方法，如果正在销毁的bean实现了DisposableBean接口
                        则调用destroy方法
                         */

                        return createBean(beanName, mbd, args);
                    }
                    catch (BeansException ex) {
                        // Explicitly remove instance from singleton cache: It might have been put there
                        // eagerly by the creation process, to allow for circular reference resolution.
                        // Also remove any beans that received a temporary reference to the bean.
                        destroySingleton(beanName);
                        throw ex;
                    }
                });
                // 如果是FactoryBean则调用getObject方法获取bean
                bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
            }

            else if (mbd.isPrototype()) {
                // It's a prototype -> create a new instance.
                Object prototypeInstance = null;
                try {
                    // 将beanName添加到prototypesCurrentlyInCreation中，在doGetBean方法每次创建bean的时候判断prototypesCurrentlyInCreation
                    // 是否存在当前正在创建的bean，如果存在则说明有循环依赖，直接报错
                    beforePrototypeCreation(beanName);
                    // 非单例bean每次都创建一个新的，所以不需要用ObjectFactory了
                    prototypeInstance = createBean(beanName, mbd, args);
                }
                finally {
                    afterPrototypeCreation(beanName);
                }
                // 如果是FactoryBean则调用getObject方法获取bean
                bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
            }

            //如果不是原型的也不是单例的则创建scope的bean
            else {
                String scopeName = mbd.getScope();
                final Scope scope = this.scopes.get(scopeName);
                if (scope == null) {
                    throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
                }
                try {
                    Object scopedInstance = scope.get(beanName, () -> {
                        beforePrototypeCreation(beanName);
                        try {
                            return createBean(beanName, mbd, args);
                        }
                        finally {
                            afterPrototypeCreation(beanName);
                        }
                    });
                    bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
                }
                catch (IllegalStateException ex) {
                    throw new BeanCreationException(beanName,
                            "Scope '" + scopeName + "' is not active for the current thread; consider " +
                            "defining a scoped proxy for this bean if you intend to refer to it from a singleton",
                            ex);
                }
            }
        }
        catch (BeansException ex) {
            cleanupAfterBeanCreationFailure(beanName);
            throw ex;
        }
    }

    // 转换bean为需要的类型
    // Check if required type matches the type of the actual bean instance.
    if (requiredType != null && !requiredType.isInstance(bean)) {
        try {
            T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
            if (convertedBean == null) {
                throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
            }
            return convertedBean;
        }
        catch (TypeMismatchException ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to convert bean '" + name + "' to required type '" +
                        ClassUtils.getQualifiedName(requiredType) + "'", ex);
            }
            throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
        }
    }
    return (T) bean;
}
```

下面是具体过程：
- 获取bean的名字，代码：
  ```java
  protected String transformedBeanName(String name) {
      return canonicalName(BeanFactoryUtils.transformedBeanName(name));
  }

  // 如果name以&开头，则删除&返回
  public static String transformedBeanName(String name) {
      Assert.notNull(name, "'name' must not be null");
      String beanName = name;
      while (beanName.startsWith(BeanFactory.FACTORY_BEAN_PREFIX)) {
          beanName = beanName.substring(BeanFactory.FACTORY_BEAN_PREFIX.length());
      }
      return beanName;
  }

  // 获取别名的原始名称，如A -> B -> C则返回C，如果不存在别名则返回传入的name
  public String canonicalName(String name) {
      String canonicalName = name;
      // Handle aliasing...
      String resolvedName;
      do {
          resolvedName = this.aliasMap.get(canonicalName);
          if (resolvedName != null) {
              canonicalName = resolvedName;
          }
      }
      while (resolvedName != null);
      return canonicalName;
  }
  ```
  如果调用`getBean()`方法时传入的beanName为`&beanName`，则表示想要获取的是[FactoryBean]，否则是[FactoryBean]创建的bean或非[FactoryBean]类型的普通bean，而[FactoryBean]在容器中是以beanName为key保存的，所以需要获取到原始的beanName来寻找[FactoryBean]
- 获取缓存中的单例bean
  ```java
  // 从缓存中获取单例bean，如果存在则返回
  Object sharedInstance = getSingleton(beanName);
  if (sharedInstance != null && args == null) {
      if (logger.isDebugEnabled()) {
          // 如果bean正在被创建，说明发生了循环引用
          if (isSingletonCurrentlyInCreation(beanName)) {
              logger.debug("Returning eagerly cached instance of singleton bean '" + beanName +
                      "' that is not fully initialized yet - a consequence of a circular reference");
          }
          else {
              logger.debug("Returning cached instance of singleton bean '" + beanName + "'");
          }
      }
      // 如果sharedInstance是一个普通bean的话直接返回，如果是FactoryBean的话返回getObject方法的返回值
      bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
  }
  ```
- 如果缓存中存在需要获取的bean，则需要判断bean是否实现了[FactoryBean]接口，和是否需要调用[FactoryBean]接口的`getObject()`方法获取bean，代码：
  ```java
  protected Object getObjectForBeanInstance(
          Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

      // Don't let calling code try to dereference the factory if the bean isn't a factory.
      if (BeanFactoryUtils.isFactoryDereference(name)) {
          if (beanInstance instanceof NullBean) {
              return beanInstance;
          }
          // 如果name以&开头但是bean不是FactoryBean类型的则报错
          if (!(beanInstance instanceof FactoryBean)) {
              throw new BeanIsNotAFactoryException(transformedBeanName(name), beanInstance.getClass());
          }
      }

      // Now we have the bean instance, which may be a normal bean or a FactoryBean.
      // If it's a FactoryBean, we use it to create a bean instance, unless the
      // caller actually wants a reference to the factory.
      // 当前bean不是FactoryBean类型的，直接返回，或者如果name是&开头的则表示想要获取的就是FactoryBean，直接返回
      if (!(beanInstance instanceof FactoryBean) || BeanFactoryUtils.isFactoryDereference(name)) {
          return beanInstance;
      }

      // 如果不满足上面的条件则表示beanInstance是FactoryBean类型的并且想要获取的是FactoryBean创建出来的bean，此时调用FactoryBean的
      // getObject方法返回bean
      Object object = null;
      if (mbd == null) {
          // 尝试从缓存中获取beanFactory产生的bean
          object = getCachedObjectForFactoryBean(beanName);
      }
      if (object == null) {
          // Return bean instance from factory.
          FactoryBean<?> factory = (FactoryBean<?>) beanInstance;
          // Caches object obtained from FactoryBean if it is a singleton.
          if (mbd == null && containsBeanDefinition(beanName)) {
              // 合并当前bean和其父bean的属性，即当前bean定义在其他bean内的话，则创建父bean的BeanDefinition并用当前bean的属性覆盖或合并父bean的属性并返回
              // 如果不存在父bean则直接根据当前bean的BeanDefinition创建RootBeanDefinition
              mbd = getMergedLocalBeanDefinition(beanName);
          }
          // synthetic表示bean是否是用户定义的，如果不是则不需要调用postProcessAfterInitialization，如为了支持<aop:config>spring会
          // 创建synthetic为true的bean
          boolean synthetic = (mbd != null && mbd.isSynthetic());
          object = getObjectFromFactoryBean(factory, beanName, !synthetic);
      }
      return object;
  }

  // 从FactoryBean中获取Bean，AbstractBeanFactory获取bean时，会判断通过反射创建出来的bean是否是FactoryBean类型的，如果是，则会调用
  // 该方法从FactoryBean中返回真正的bean，一般情况下bean实现FactoryBean接口时不直接实现，而是实现FactoryBean接口的的抽象实现类AbstractFactoryBean
  // 该类通过模版方法模式，使得用户实现FactoryBean接口时只需要关心如何创建对象
  protected Object getObjectFromFactoryBean(FactoryBean<?> factory, String beanName, boolean shouldPostProcess) {

      // 如果FactoryBean是单例的并且FactoryBean对象已经保存到singletonObjects中，即已经添加到单例bean的缓存中
      if (factory.isSingleton() && containsSingleton(beanName)) {
          synchronized (getSingletonMutex()) {
              // 尝试从FactoryBean name --> object的map中获取bean
              Object object = this.factoryBeanObjectCache.get(beanName);
              if (object == null) {
                  // 调用factory.getObject()方法获取bean
                  object = doGetObjectFromFactoryBean(factory, beanName);
                  // Only post-process and store if not put there already during getObject() call above
                  // (e.g. because of circular reference processing triggered by custom getBean calls)
                  Object alreadyThere = this.factoryBeanObjectCache.get(beanName);
                  if (alreadyThere != null) {
                      object = alreadyThere;
                  }
                  else {
                      // 是否需要执行后置处理
                      if (shouldPostProcess) {
                          if (isSingletonCurrentlyInCreation(beanName)) {
                              // Temporarily return non-post-processed object, not storing it yet..
                              return object;
                          }
                          // 将beanName添加到singletonsCurrentlyInCreation中
                          beforeSingletonCreation(beanName);
                          try {
                              // 遍历BeanPostProcessor调用postProcessAfterInitialization方法
                              object = postProcessObjectFromFactoryBean(object, beanName);
                          }
                          catch (Throwable ex) {
                              throw new BeanCreationException(beanName,
                                      "Post-processing of FactoryBean's singleton object failed", ex);
                          }
                          finally {
                              // 从singletonsCurrentlyInCreation中删除beanName
                              afterSingletonCreation(beanName);
                          }
                      }
                      // 以FactoryBean name --> object的形式将bean添加到缓存中
                      if (containsSingleton(beanName)) {
                          this.factoryBeanObjectCache.put(beanName, object);
                      }
                  }
              }
              return object;
          }
      }
      else {
          // 不是单例或者FactoryBean还没有注册到singletonObjects的话直接创建新的对象
          Object object = doGetObjectFromFactoryBean(factory, beanName);
          if (shouldPostProcess) {
              try {
                  object = postProcessObjectFromFactoryBean(object, beanName);
              }
              catch (Throwable ex) {
                  throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
              }
          }
          return object;
      }
  }
  ```
  `getObjectForBeanInstance()`方法处理了bean为[FactoryBean]时的逻辑，当beanName不是&开头并且bean实现了[FactoryBean]接口，则调用该接口的`getObject()`方法获取实际的bean，一般情况下bean不直接实现[FactoryBean]接口，而是实现[FactoryBean]接口的的抽象实现类[AbstractFactoryBean]，该类通过模版方法模式，使得用户实现[FactoryBean]接口时只需要关心如何创建对象，具体用法可以看笔记[FactoryBean的使用和实现原理](../容器的使用/FactoryBean的使用和实现原理.md)
- 以上是缓存中存在bean时的处理，如果缓存中不存在bean，即bean还没有被创建过，则执行创建逻辑，首先是创建前的前置条件检查：
  ```java
  // 对于以prototype为scope的bean，如果创建过程中发现当前bean已经处于创建过程，则抛出异常，防止scope为prototype的bean之间的循环引用
  if (isPrototypeCurrentlyInCreation(beanName)) {
      throw new BeanCurrentlyInCreationException(beanName);
  }

  // Check if bean definition exists in this factory.
  // 判断是否存在父BeanFactory，如果存在并且当前BeanFactory的BeanDefinition中不包含正在创建的bean对应的BeanDefinition，则从父BeanFactory获取bean
  BeanFactory parentBeanFactory = getParentBeanFactory();
  if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
      // Not found -> check parent.
      // 还原beanName，transformedBeanName方法的相反操作，如果是FactoryBean则返回&beanName
      String nameToLookup = originalBeanName(name);
      if (parentBeanFactory instanceof AbstractBeanFactory) {
          return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
                  nameToLookup, requiredType, args, typeCheckOnly);
      }
      else if (args != null) {
          // Delegation to parent with explicit args.
          return (T) parentBeanFactory.getBean(nameToLookup, args);
      }
      else {
          // No args -> delegate to standard getBean method.
          return parentBeanFactory.getBean(nameToLookup, requiredType);
      }
  }

  // 如果不仅仅是类型检查则将当前bean置成已创建状态，即保存到alreadyCreated中
  if (!typeCheckOnly) {
      markBeanAsCreated(beanName);
  }

  try {
      // 获取bean的BeanDefinition
      final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
      // 如果BeanDefinition声明了bean是abstract的则报错
      checkMergedBeanDefinition(mbd, beanName, args);

      // Guarantee initialization of beans that the current bean depends on.
      // 确保当前bean声明的dependOn的bean已经被初始化了，这里的dependOn不同于bean的属性引用的依赖，而是用户自己声明的
      // 如BeanA可以声明dependOn BeanB，即使两个bean没有关系，dependOn用于确保创建bean之前已经创建了某些bean，
      // 并且dependOn不可以循环依赖
      String[] dependsOn = mbd.getDependsOn();
      if (dependsOn != null) {
          // 如果存在dependsOn则遍历并创建
          for (String dep : dependsOn) {
              // 判断dep是否依赖beanName，如果是则为循环依赖，报错
              if (isDependent(beanName, dep)) {
                  throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                          "Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
              }
              // 保存beanName依赖dep的依赖关系
              registerDependentBean(dep, beanName);
              try {
                  // 创建依赖，如果不存在则抛出异常
                  getBean(dep);
              }
              catch (NoSuchBeanDefinitionException ex) {
                  throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                          "'" + beanName + "' depends on missing bean '" + dep + "'", ex);
              }
          }
      }
  ```
- 在满足创建条件以后，开始执行创建逻辑：
  ```java
  // 如果bean是单例的
  if (mbd.isSingleton()) {
      // 单例bean是支持循环引用的，处理过程是，这里先创建一个ObjectFactory（下面的lambda就是ObjectFactory实例），在getSingleton中将会调用这里的匿名ObjectFactory的
      // createBean方法，而createBean方法会调用doCreateBean方法创建bean，doCreateBean在创建bean时解析构造函数或者工厂方法
      // 创建一个没有注入任何属性的简单bean，在创建完成后执行addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
      // 将能够返回刚创建的bean的匿名ObjectFactory添加到内存中，getEarlyBeanReference遍历所有的SmartInstantiationAwareBeanPostProcessor对bean做一些特殊处理，AOP就是通过
      // SmartInstantiationAwareBeanPostProcessor实现的，如果不存在SmartInstantiationAwareBeanPostProcessor则直接返回简单bean。再将ObjectFactory添加到内存中后将继续该bean的创建，
      // 首先是populateBean方法，该方法将填充bean的属性，而填充属性就可能导致循环引用，假设当前正在创建的bean是beanA，而beanA有属性beanB，populateBean就将填充beanB到beanA，
      // 由于之前没有创建过beanB，所以填充之前将会创建一个beanB，如果beanB也有属性beanA，则存在循环引用，在创建beanB的时候将会尝试获取beanA，获取方式就是执行getBean(beanA)，调用getBean(beanA)使得
      // getSingleton(beanA)方法被调用，该方法将会获取到之前添加到内存的匿名beanFactory并通过该beanFactory的getBean方法获取到刚创建完成正在
      // 填充属性的beanA，在获取到beanA后beanB就能够顺利初始化了，之后返回beanB并继续beanA的创建，以上就是解决循环引用的过程
      sharedInstance = getSingleton(beanName, () -> {
          try {
              /*
              该方法创建bean，创建过程中涉及到了多个接口方法调用，顺序和用途如下:
              1.如果当前BeanFactory存在InstantiationAwareBeanPostProcessor则调用该InstantiationAwareBeanPostProcessor的postProcessBeforeInstantiation方法，
              如果方法返回了一个对象则以该对象作为bean返回并在返回前遍历所有的BeanPostProcessor调用postProcessAfterInitialization方法(该方法的设计目的是在bean及其属性初始化完毕后调用，
              由于InstantiationAwareBeanPostProcessor的postProcessBeforeInstantiation返回的对象将作为最终的bean，所以需要在返回前调用该方法)对bean进行处理，如果遍历过程中某个
              BeanPostProcessor的postProcessAfterInitialization返回null则结束遍历，否则以返回的对象为bean并继续遍历
              2.如果正在创建的bean有Supplier则以Supplier提供的对象为bean，否则如果存在工厂方法则以工厂方法的返回结果做为bean，否则如果存在构造函数参数则根据构造函数参数，自动解析所有构造函数，
              或在SmartInstantiationAwareBeanPostProcessor接口的返回的构造函数列表中，获取满足构造函数参数的构造函数并创建bean，否则以默认构造函数创建bean
              3.获取所有的MergedBeanDefinitionPostProcessor调用postProcessMergedBeanDefinition对正在创建的bean的BeanDefinition就行处理
              4.根据创建出来的bean创建ObjectFactory并将ObjectFactory添加到singletonFactories以支持循环依赖
              5.开始设置bean的属性，在此之前再次获取所有的InstantiationAwareBeanPostProcessor，执行postProcessAfterInstantiation方法，目的是在设置属性之前对bean做定制，
              遍历过程中如果某个InstantiationAwareBeanPostProcessor的postProcessAfterInstantiation返回false则结束遍历
              6.根据autowire的设置，如果是byType或byName则用相应的策略填充属性，对于循环依赖的属性解决方法在上面的注释已经说了
              7.如果bean实现了BeanNameAware、BeanClassLoaderAware或者BeanFactoryAware则调用相应的接口方法
              8.遍历所有的BeanPostProcessor调用postProcessBeforeInitialization方法，此时bean的依赖都注入了
              9.如果bean实现了InitializingBean接口则调用afterPropertiesSet方法，如果bean存在init-method则调用自定义的方法
              10.遍历所有的BeanPostProcessor调用postProcessAfterInitialization方法
              11.注册当前bean的DisposableBeanAdapter对象用于在销毁bean时遍历DestructionAwareBeanPostProcessor调用回调方法，如果正在销毁的bean实现了DisposableBean接口
              则调用destroy方法
               */

              return createBean(beanName, mbd, args);
          }
          catch (BeansException ex) {
              // Explicitly remove instance from singleton cache: It might have been put there
              // eagerly by the creation process, to allow for circular reference resolution.
              // Also remove any beans that received a temporary reference to the bean.
              destroySingleton(beanName);
              throw ex;
          }
      });
      // 如果是FactoryBean则调用getObject方法获取bean
      bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
  }
  ```
- 上面的`getSingleton()`方法接收beanName和一个[ObjectFactory]实例，上面用lambda定义了一个返回`createBean()`方法结果的[ObjectFactory]实例，`getSingleton()`方法代码：
  ```java
  public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
      Assert.notNull(beanName, "Bean name must not be null");
      synchronized (this.singletonObjects) {
          // 先尝试从缓存中获取单例bean
          Object singletonObject = this.singletonObjects.get(beanName);
          if (singletonObject == null) {
              // 如果正在销毁所有的单例bean则报错，防止在调用bean的销毁方法时创建bean
              if (this.singletonsCurrentlyInDestruction) {
                  throw new BeanCreationNotAllowedException(beanName,
                          "Singleton bean creation not allowed while singletons of this factory are in destruction " +
                          "(Do not request a bean from a BeanFactory in a destroy method implementation!)");
              }
              if (logger.isDebugEnabled()) {
                  logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
              }
              // 将beanName添加到singletonsCurrentlyInCreation，表示正在创建该单例bean
              beforeSingletonCreation(beanName);
              boolean newSingleton = false;
              boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
              if (recordSuppressedExceptions) {
                  this.suppressedExceptions = new LinkedHashSet<>();
              }
              try {
                  // 创建单例bean，singletonFactory是使用lambda创建的，getObject方法返回的实际上就是createBean方法的返回值
                  singletonObject = singletonFactory.getObject();
                  newSingleton = true;
              }
              catch (IllegalStateException ex) {
                  // Has the singleton object implicitly appeared in the meantime ->
                  // if yes, proceed with it since the exception indicates that state.
                  singletonObject = this.singletonObjects.get(beanName);
                  if (singletonObject == null) {
                      throw ex;
                  }
              }
              catch (BeanCreationException ex) {
                  if (recordSuppressedExceptions) {
                      for (Exception suppressedException : this.suppressedExceptions) {
                          ex.addRelatedCause(suppressedException);
                      }
                  }
                  throw ex;
              }
              finally {
                  if (recordSuppressedExceptions) {
                      this.suppressedExceptions = null;
                  }
                  // 从singletonsCurrentlyInCreation移除beanName
                  afterSingletonCreation(beanName);
              }
              // 将bean保存到singletonObjects中缓存下来
              if (newSingleton) {
                  addSingleton(beanName, singletonObject);
              }
          }
          return singletonObject;
      }
  }
  ```
- `getSingleton()`方法只是维护了单例的缓存，创建单例bean的代码在传入的[ObjectFactory]中，实际上就是`createBean()`方法，[AbstractBeanFactory]中`createBean()`方法是个抽象方法，实现在[AbstractAutowireCapableBeanFactory]，代码：
  ```java
  protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
          throws BeanCreationException {

      if (logger.isDebugEnabled()) {
          logger.debug("Creating instance of bean '" + beanName + "'");
      }
      RootBeanDefinition mbdToUse = mbd;

      // Make sure bean class is actually resolved at this point, and
      // clone the bean definition in case of a dynamically resolved Class
      // which cannot be stored in the shared merged bean definition.
      /*
       获取bean的类型，从bean配置中的class属性解析而来，如：
       <bean id="myBeanA" class="org.springframework.tests.sample.beans.MyBeanAFactoryBean"/>
        */
      Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
      if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
          mbdToUse = new RootBeanDefinition(mbd);
          mbdToUse.setBeanClass(resolvedClass);
      }

      // Prepare method overrides.
      try {
          // 验证methodOverrides代理的方法在指定的Class中都存在并且判断被代理的方法存不存在重载，如果不存在则
          // 设置overloaded属性为false，使得后面需要查找被代理方法时不需要做过多的分析操作节省性能
          mbdToUse.prepareMethodOverrides();
      }
      catch (BeanDefinitionValidationException ex) {
          throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
                  beanName, "Validation of method overrides failed", ex);
      }

      try {
          // Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
          // 主要针对InstantiationAwareBeanPostProcessor接口，如果存在实现了InstantiationAwareBeanPostProcessor接口的bean，则遍历并逐个调用postProcessBeforeInstantiation方法，
          // 如果最终返回的对象不为空则以返回值作为bean，并遍历所有实现了BeanPostProcessor接口的bean，传入刚创建的bean，逐个调用postProcessAfterInitialization方法处理bean后返回
          Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
          if (bean != null) {
              return bean;
          }
      }
      catch (Throwable ex) {
          throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
                  "BeanPostProcessor before instantiation of bean failed", ex);
      }

      try {
          // 创建bean
          Object beanInstance = doCreateBean(beanName, mbdToUse, args);
          if (logger.isDebugEnabled()) {
              logger.debug("Finished creating instance of bean '" + beanName + "'");
          }
          return beanInstance;
      }
      catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
          // A previously detected exception with proper bean creation context already,
          // or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
          throw ex;
      }
      catch (Throwable ex) {
          throw new BeanCreationException(
                  mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
      }
  }
  ```
- `createBean()`方法主要是在创建bean之前遍历实现了[InstantiationAwareBeanPostProcessor]接口的bean并逐个调用`postProcessBeforeInstantiation()`方法，如果遍历过程中返回了非空实例，则直接作为bean使用，不再执行后面创建bean的过程，[InstantiationAwareBeanPostProcessor]接口的一个使用场景是AOP中的`TargetSource`功能，文档：
  
  https://docs.spring.io/spring/docs/5.1.3.RELEASE/spring-framework-reference/core.html#aop-targetsource
  
  如果没有从[InstantiationAwareBeanPostProcessor]接口中获取到bean，则执行`doCreateBean()`方法，代码：
  ```java
  protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final @Nullable Object[] args)
          throws BeanCreationException {

      // Instantiate the bean.
      // BeanWrapper实例用于维护正在创建中的bean和该bean的PropertyDescriptor列表，PropertyDescriptor的作用是对bean的某个属性
      // 进行读写
      BeanWrapper instanceWrapper = null;
      // bean默认就是singleton
      if (mbd.isSingleton()) {
          instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
      }
      if (instanceWrapper == null) {
          // 创建bean并保存到instanceWrapper中，如果bean存在lookupMethod或replaceMethod等methodOverride属性则调用cglib实例化bean
          // 否则默认使用反射创建bean
          instanceWrapper = createBeanInstance(beanName, mbd, args);
      }
      final Object bean = instanceWrapper.getWrappedInstance();
      Class<?> beanType = instanceWrapper.getWrappedClass();
      if (beanType != NullBean.class) {
          mbd.resolvedTargetType = beanType;
      }

      // Allow post-processors to modify the merged bean definition.
      synchronized (mbd.postProcessingLock) {
          // 如果当前BeanDefinition还没有被MergedBeanDefinitionPostProcessor处理过
          if (!mbd.postProcessed) {
              try {
                  // 调用MergedBeanDefinitionPostProcessor接口的postProcessMergedBeanDefinition方，Autowired注解就是通过该接口实现的
                  applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
              }
              catch (Throwable ex) {
                  throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                          "Post-processing of merged bean definition failed", ex);
              }
              mbd.postProcessed = true;
          }
      }

      // Eagerly cache singletons to be able to resolve circular references
      // even when triggered by lifecycle interfaces like BeanFactoryAware.
      boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
              isSingletonCurrentlyInCreation(beanName));
      if (earlySingletonExposure) {
          if (logger.isDebugEnabled()) {
              logger.debug("Eagerly caching bean '" + beanName +
                      "' to allow for resolving potential circular references");
          }
          // 添加匿名的beanFactory到缓存中以支持循环引用，这里返回bean调用的是getEarlyBeanReference方法，该方法调用SmartInstantiationAwareBeanPostProcessor接口的
          // getEarlyBeanReference方法对bean做某些处理
          addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
      }

      // Initialize the bean instance.
      Object exposedObject = bean;
      try {
          // 填充bean的属性
          populateBean(beanName, mbd, instanceWrapper);
          // 调用初始化方法，如init-method
          exposedObject = initializeBean(beanName, exposedObject, mbd);
      }
      catch (Throwable ex) {
          if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
              throw (BeanCreationException) ex;
          }
          else {
              throw new BeanCreationException(
                      mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
          }
      }

      // 检查在循环引用的情况下其他bean引入的是原始bean而原始bean又做了代理配置导致和原始的bean不一样了的情况
      // 如果存在这种情况则报错
      if (earlySingletonExposure) {
          Object earlySingletonReference = getSingleton(beanName, false);
          if (earlySingletonReference != null) {
              // 判断是否是原始bean
              if (exposedObject == bean) {
                  exposedObject = earlySingletonReference;
              }
              // 是否允许自动注入被包装过的bean，如果不允许则当前bean是否存在依赖它的bean，如果存在说明其他bean注入了和当前bean不一样的bean，报错
              else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
                  String[] dependentBeans = getDependentBeans(beanName);
                  Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
                  for (String dependentBean : dependentBeans) {
                      if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
                          actualDependentBeans.add(dependentBean);
                      }
                  }
                  if (!actualDependentBeans.isEmpty()) {
                      throw new BeanCurrentlyInCreationException(beanName,
                              "Bean with name '" + beanName + "' has been injected into other beans [" +
                              StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
                              "] in its raw version as part of a circular reference, but has eventually been " +
                              "wrapped. This means that said other beans do not use the final version of the " +
                              "bean. This is often the result of over-eager type matching - consider using " +
                              "'getBeanNamesOfType' with the 'allowEagerInit' flag turned off, for example.");
                  }
              }
          }
      }

      // Register bean as disposable.
      try {
          // 注册bean的DisposableBeanAdapter，用于在销毁bean时执行DestructionAwareBeanPostProcessor的回调，如果bean实现了
          // DisposableBean接口则DisposableBeanAdapter还会执行bean的destory方法
          registerDisposableBeanIfNecessary(beanName, bean, mbd);
      }
      catch (BeanDefinitionValidationException ex) {
          throw new BeanCreationException(
                  mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
      }

      return exposedObject;
  }
  ```
  创建bean的过程首先调用`createBeanInstance()`方法创建[BeanWrapper]，`createBeanInstance()`方法代码：
  ```java
  protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
      // Make sure bean class is actually resolved at this point.
      Class<?> beanClass = resolveBeanClass(mbd, beanName);

      if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
          throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                  "Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
      }

      // 如果指定了supplier，则直接调用其get方法返回bean
      Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
      if (instanceSupplier != null) {
          return obtainFromSupplier(instanceSupplier, beanName);
      }

      // 如果存在工厂方法则使用工厂方法创建bean
      if (mbd.getFactoryMethodName() != null)  {
          return instantiateUsingFactoryMethod(beanName, mbd, args);
      }

      // Shortcut when re-creating the same bean...
      boolean resolved = false;
      boolean autowireNecessary = false;
      if (args == null) {
          synchronized (mbd.constructorArgumentLock) {
              // 如果已经解析过构造函数则直接使用，不需要再次解析
              if (mbd.resolvedConstructorOrFactoryMethod != null) {
                  resolved = true;
                  autowireNecessary = mbd.constructorArgumentsResolved;
              }
          }
      }
      // 如果已经解析过构造函数则直接尝试创建
      if (resolved) {
          // 如果构造函数参数中有需要注入的类型
          if (autowireNecessary) {
              return autowireConstructor(beanName, mbd, null, null);
          }
          else {
              // 否则通过反射实例化bean，如果存在代理方法则使用cglib实例化代理bean
              return instantiateBean(beanName, mbd);
          }
      }

      // 否则调用SmartInstantiationAwareBeanPostProcessor接口的determineCandidateConstructors方法获取构造函数
      // Need to determine the constructor...
      Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
      if (ctors != null ||
              mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_CONSTRUCTOR ||
              mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args))  {
          // 通过构造函数参数找到匹配的构造函数并创建bean，ctors为SmartInstantiationAwareBeanPostProcessor返回的可用构造函数
          // 数组，可能为空
          return autowireConstructor(beanName, mbd, ctors, args);
      }

      // No special handling: simply use no-arg constructor.
      // 如果通过SmartInstantiationAwareBeanPostProcessor接口没找到构造函数或者当前bean没有构造函数参数，则使用无参构造函数
      return instantiateBean(beanName, mbd);
  }
  ```
  上面的方法解析了构造函数并实例化bean，以最简单的无参构造函数实例化为例，代码：
  ```java
  protected BeanWrapper instantiateBean(final String beanName, final RootBeanDefinition mbd) {
      try {
          Object beanInstance;
          final BeanFactory parent = this;
          if (System.getSecurityManager() != null) {
              beanInstance = AccessController.doPrivileged((PrivilegedAction<Object>) () ->
                      getInstantiationStrategy().instantiate(mbd, beanName, parent),
                      getAccessControlContext());
          }
          else {
              // 默认实例化策略的实现是CglibSubclassingInstantiationStrategy，对于普通bean，该类的实现是反射，如果
              // bean有lookup method或replace method则使用cglib实例化bean
              beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, parent);
          }
          BeanWrapper bw = new BeanWrapperImpl(beanInstance);
          initBeanWrapper(bw);
          return bw;
      }
      catch (Throwable ex) {
          throw new BeanCreationException(
                  mbd.getResourceDescription(), beanName, "Instantiation of bean failed", ex);
      }
  }
  ```
  实例化bean后将bean保存到[BeanWrapper]中，此时bean的属性还没有被填充，而[BeanWrapper]实现了[PropertyAccessor]接口，能够对bean的属性进行读写，关于属性注册可以看笔记[bean的属性填充过程](bean的属性填充过程.md)
- 回到`doCreateBean()`方法，在创建了[BeanWrapper]后，执行`applyMergedBeanDefinitionPostProcessors()`方法，该方法遍历所有的[MergedBeanDefinitionPostProcessor]并执行`postProcessMergedBeanDefinition()`方法，目的是在实例化bean之后，填充bean属性之前对bean的[BeanDefinition]进行操作，Autowired注解的实现就用到了[MergedBeanDefinitionPostProcessor]接口，这一部分可以看笔记[常用注解的实现](常用注解的实现.md)。执行`applyMergedBeanDefinitionPostProcessors()`方法之后执行`doCreateBean()`方法，代码：
  ```java
  protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final @Nullable Object[] args)
          throws BeanCreationException {
      //...省略

      // Eagerly cache singletons to be able to resolve circular references
      // even when triggered by lifecycle interfaces like BeanFactoryAware.
      boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
              isSingletonCurrentlyInCreation(beanName));
      // 如果bean是单例的并且允许循环引用
      if (earlySingletonExposure) {
          if (logger.isDebugEnabled()) {
              logger.debug("Eagerly caching bean '" + beanName +
                      "' to allow for resolving potential circular references");
          }
          // 添加匿名的beanFactory到缓存中以支持循环引用，这里返回bean调用的是getEarlyBeanReference方法，该方法遍历所有的
          // SmartInstantiationAwareBeanPostProcessor并调用getEarlyBeanReference方法，AOP的实现就用到了SmartInstantiationAwareBeanPostProcessor
          addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
      }

      // Initialize the bean instance.
      Object exposedObject = bean;
      try {
          // 填充bean的属性
          populateBean(beanName, mbd, instanceWrapper);
          // 调用初始化方法，如init-method
          exposedObject = initializeBean(beanName, exposedObject, mbd);
      }
      catch (Throwable ex) {
          if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
              throw (BeanCreationException) ex;
          }
          else {
              throw new BeanCreationException(
                      mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
          }
      }

      // 检查在循环引用的情况下其他bean引入的是原始bean而原始bean又做了代理配置导致和原始的bean不一样了的情况
      // 如果存在这种情况则报错
      if (earlySingletonExposure) {
          Object earlySingletonReference = getSingleton(beanName, false);
          if (earlySingletonReference != null) {
              // 判断是否是原始bean
              if (exposedObject == bean) {
                  exposedObject = earlySingletonReference;
              }
              // 是否允许自动注入被包装过的bean，如果不允许则当前bean是否存在依赖它的bean，如果存在说明其他bean注入了和当前bean不一样的bean，报错
              else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
                  String[] dependentBeans = getDependentBeans(beanName);
                  Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
                  for (String dependentBean : dependentBeans) {
                      if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
                          actualDependentBeans.add(dependentBean);
                      }
                  }
                  if (!actualDependentBeans.isEmpty()) {
                      throw new BeanCurrentlyInCreationException(beanName,
                              "Bean with name '" + beanName + "' has been injected into other beans [" +
                              StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
                              "] in its raw version as part of a circular reference, but has eventually been " +
                              "wrapped. This means that said other beans do not use the final version of the " +
                              "bean. This is often the result of over-eager type matching - consider using " +
                              "'getBeanNamesOfType' with the 'allowEagerInit' flag turned off, for example.");
                  }
              }
          }
      }

      // Register bean as disposable.
      try {
          // 注册bean的DisposableBeanAdapter，用于在销毁bean时执行DestructionAwareBeanPostProcessor的回调，如果bean实现了
          // DisposableBean接口则DisposableBeanAdapter还会执行bean的destory方法
          registerDisposableBeanIfNecessary(beanName, bean, mbd);
      }
      catch (BeanDefinitionValidationException ex) {
          throw new BeanCreationException(
                  mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
      }

      return exposedObject;
  }
  ```
  `addSingletonFactory()`方法接收bean和[ObjectFactory]，用于支持循环引用，代码：
  ```java
  protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
      Assert.notNull(singletonFactory, "Singleton factory must not be null");
      synchronized (this.singletonObjects) {
          // 如果单例缓存中还没有当前bean
          if (!this.singletonObjects.containsKey(beanName)) {
              // 将singletonFactory缓存起来
              this.singletonFactories.put(beanName, singletonFactory);
              // earlySingletonObjects的作用是缓存从singletonFactory中获取的bean
              this.earlySingletonObjects.remove(beanName);
              // registeredSingletons按照创建顺序保存beanName
              this.registeredSingletons.add(beanName);
          }
      }
  }
  ```
  单独看`addSingletonFactory()`方法看不出该方法的作用，需要结合后面处理单例bean的循环依赖时来看，现在只需要记住，`addSingletonFactory()`方法将`singletonFactory`保存到了`singletonFactories`

  在执行`addSingletonFactory()`方法之后，执行了`populateBean()`方法，代码：
  ```java
  protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
      if (bw == null) {
          if (mbd.hasPropertyValues()) {
              throw new BeanCreationException(
                      mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
          }
          else {
              // Skip property population phase for null instance.
              return;
          }
      }

      // Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
      // state of the bean before properties are set. This can be used, for example,
      // to support styles of field injection.
      boolean continueWithPropertyPopulation = true;

      // 给InstantiationAwareBeanPostProcessors机会在设置属性之前改变bean
      if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
          for (BeanPostProcessor bp : getBeanPostProcessors()) {
              if (bp instanceof InstantiationAwareBeanPostProcessor) {
                  InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
                  if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
                      continueWithPropertyPopulation = false;
                      break;
                  }
              }
          }
      }

      // 如果postProcessAfterInstantiation返回false则停止自动注入属性的过程
      if (!continueWithPropertyPopulation) {
          return;
      }

      // 获取所有从XML的property元素解析到的属性
      // 可能是RuntimeBeanReference或者是TypedStringValue
      PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

      // 判断属性的注入方式，通过在XML配置bean时设置autowire属性指定，如果设置了autowire则尝试自动注入bean的属性(即使没有在属性上声明注解)
      if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME ||
              mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
          MutablePropertyValues newPvs = new MutablePropertyValues(pvs);

          // Add property values based on autowire by name if applicable.
          if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME) {
              // 根据属性名称获取bean，获取到的bean保存在newPvs中
              autowireByName(beanName, mbd, bw, newPvs);
          }

          // Add property values based on autowire by type if applicable.
          if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
              // 根据属性类型获取bean，获取到的bean保存在newPvs中
              autowireByType(beanName, mbd, bw, newPvs);
          }

          pvs = newPvs;
      }

      boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
      boolean needsDepCheck = (mbd.getDependencyCheck() != RootBeanDefinition.DEPENDENCY_CHECK_NONE);

      // 如果存在InstantiationAwareBeanPostProcessors并且当前bean没有禁用依赖检查，则执行postProcessPropertyValues方法
      if (hasInstAwareBpps || needsDepCheck) {
          if (pvs == null) {
              pvs = mbd.getPropertyValues();
          }
          PropertyDescriptor[] filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
          if (hasInstAwareBpps) {
              for (BeanPostProcessor bp : getBeanPostProcessors()) {
                  if (bp instanceof InstantiationAwareBeanPostProcessor) {
                      InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
                      pvs = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
                      if (pvs == null) {
                          return;
                      }
                  }
              }
          }
          if (needsDepCheck) {
              checkDependencies(beanName, mbd, filteredPds, pvs);
          }
      }

      // 设置属性到bean中
      if (pvs != null) {
          applyPropertyValues(beanName, mbd, bw, pvs);
      }
  }
  ```
- 执行了`populateBean()`实现了bean的属性注入逻辑，注入的属性可以是其他bean，如果其他bean还没有创建则会调用`getBean()`方法创建bean，此时可能会发生单例bean的循环引用，如beanA有属性beanB，beanB有属性beanA，假设先创建beanA，则在创建beanA时尝试创建beanB，而beanB又依赖了beanA。Spring通过[ObjectFactory]解决单例bean的循环依赖，具体实现方法是：
  - 调用`getBean`方法时会先执行`getSingleton(String beanName)`方法获取缓存中的单例bean，代码：
    ```java
    public Object getSingleton(String beanName) {
        return getSingleton(beanName, true);
    }

    protected Object getSingleton(String beanName, boolean allowEarlyReference) {
        Object singletonObject = this.singletonObjects.get(beanName);
        // 如果bean还没保存到单例bean缓存中并且该bean正在被创建
        if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
            synchronized (this.singletonObjects) {
                // 尝试获取earlySingletonObjects中的bean，earlySingletonObjects保存的是之前已经从ObjectFactory获取过的bean
                singletonObject = this.earlySingletonObjects.get(beanName);
                if (singletonObject == null && allowEarlyReference) {
                    // 如果allowEarlyReference为true则从singletonFactories获取ObjectFactory用于创建bean
                    ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
                    if (singletonFactory != null) {
                        singletonObject = singletonFactory.getObject();
                        // 保存到earlySingletonObjects防止重复调用singletonFactory.getObject()，下次再获取的时候直接从earlySingletonObjects返回
                        this.earlySingletonObjects.put(beanName, singletonObject);
                        this.singletonFactories.remove(beanName);
                    }
                }
            }
        }
        return singletonObject;
    }
    ```
    
  - `getSingleton()`方法在未获取到单例bean时会判断正要获取的bean是否正在被创建，如果是则从`singletonFactories`中获取该bean的[ObjectFactory]并从[ObjectFactory]获取bean。创建bean的过程涉及到了两个[ObjectFactory]，第一个是`getBean`方法中执行的`getSingleton(String beanName, ObjectFactory<?> singletonFactory)`方法，代码：
    ```java
    sharedInstance = getSingleton(beanName, () -> {
        try {
            return createBean(beanName, mbd, args);
        }
        catch (BeansException ex) {
            destroySingleton(beanName);
            throw ex;
        }
    });

    public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
        Assert.notNull(beanName, "Bean name must not be null");
        synchronized (this.singletonObjects) {
            Object singletonObject = this.singletonObjects.get(beanName);
            if (singletonObject == null) {
                if (this.singletonsCurrentlyInDestruction) {
                    throw new BeanCreationNotAllowedException(beanName,
                            "Singleton bean creation not allowed while singletons of this factory are in destruction " +
                            "(Do not request a bean from a BeanFactory in a destroy method implementation!)");
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
                }
                // 将beanName添加到singletonsCurrentlyInCreation，表示正在创建该单例bean
                beforeSingletonCreation(beanName);
                boolean newSingleton = false;
                boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
                if (recordSuppressedExceptions) {
                    this.suppressedExceptions = new LinkedHashSet<>();
                }
                try {
                    singletonObject = singletonFactory.getObject();
                    newSingleton = true;
                }
                catch (IllegalStateException ex) {
                    singletonObject = this.singletonObjects.get(beanName);
                    if (singletonObject == null) {
                        throw ex;
                    }
                }
                catch (BeanCreationException ex) {
                    if (recordSuppressedExceptions) {
                        for (Exception suppressedException : this.suppressedExceptions) {
                            ex.addRelatedCause(suppressedException);
                        }
                    }
                    throw ex;
                }
                finally {
                    if (recordSuppressedExceptions) {
                        this.suppressedExceptions = null;
                    }
                    afterSingletonCreation(beanName);
                }
                // 将bean保存到singletonObjects中缓存下来
                if (newSingleton) {
                    addSingleton(beanName, singletonObject);
                }
            }
            return singletonObject;
        }
    }
    ```
    
  - 这第一个[ObjectFactory]的目的主要是在调用[ObjectFactory]的`getObject()`方法前将beanName添加到`singletonsCurrentlyInCreation`，这对应了一开始说的在未获取到单例bean时会判断正要获取的bean是否正在被创建；在获取到bean后将bean从`singletonsCurrentlyInCreation`移除并添加到单例缓存中，而这里的[ObjectFactory]的`getObject()`实际上就是`getBean`方法中传入`getSingleton(String beanName, ObjectFactory<?> singletonFactory)`方法的lambda表达式中的`createBean(beanName, mbd, args)`方法，代码：
    ```java
    protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
            throws BeanCreationException {

        if (logger.isDebugEnabled()) {
            logger.debug("Creating instance of bean '" + beanName + "'");
        }
        RootBeanDefinition mbdToUse = mbd;

        Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
        if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
            mbdToUse = new RootBeanDefinition(mbd);
            mbdToUse.setBeanClass(resolvedClass);
        }

        try {
            mbdToUse.prepareMethodOverrides();
        }
        catch (BeanDefinitionValidationException ex) {
            throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
                    beanName, "Validation of method overrides failed", ex);
        }

        try {
            Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
            if (bean != null) {
                return bean;
            }
        }
        catch (Throwable ex) {
            throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
                    "BeanPostProcessor before instantiation of bean failed", ex);
        }

        try {
            // 创建bean
            Object beanInstance = doCreateBean(beanName, mbdToUse, args);
            if (logger.isDebugEnabled()) {
                logger.debug("Finished creating instance of bean '" + beanName + "'");
            }
            return beanInstance;
        }
        catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
            throw ex;
        }
        catch (Throwable ex) {
            throw new BeanCreationException(
                    mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
        }
    }

    // 创建bean
    protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final @Nullable Object[] args)
            throws BeanCreationException {

        //...

        boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
                isSingletonCurrentlyInCreation(beanName));
        if (earlySingletonExposure) {
            if (logger.isDebugEnabled()) {
                logger.debug("Eagerly caching bean '" + beanName +
                        "' to allow for resolving potential circular references");
            }
            // 添加匿名的beanFactory到缓存中以支持循环引用，这里返回bean调用的是getEarlyBeanReference方法，该方法遍历所有的
            // SmartInstantiationAwareBeanPostProcessor并调用getEarlyBeanReference方法，AOP的实现就用到了SmartInstantiationAwareBeanPostProcessor
            addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
        }

        Object exposedObject = bean;
        try {
            // 填充bean的属性
            populateBean(beanName, mbd, instanceWrapper);
            // 调用初始化方法，如init-method
            exposedObject = initializeBean(beanName, exposedObject, mbd);
        }
        catch (Throwable ex) {
            if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
                throw (BeanCreationException) ex;
            }
            else {
                throw new BeanCreationException(
                        mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
            }
        }
       //...
    }
    ```

  - `createBean()`方法调用了`doCreateBean()`方法创建bean，而`doCreateBean()`方法在通过反射或cglib初始化了一个bean后执行了
    `addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean))`，代码：
    ```java
    protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
        Assert.notNull(singletonFactory, "Singleton factory must not be null");
        synchronized (this.singletonObjects) {
            // 如果单例缓存中还没有当前bean
            if (!this.singletonObjects.containsKey(beanName)) {
                // 将singletonFactory缓存起来
                this.singletonFactories.put(beanName, singletonFactory);
                this.earlySingletonObjects.remove(beanName);
                // registeredSingletons按照创建顺序保存beanName
                this.registeredSingletons.add(beanName);
            }
        }
    }
    ```
    这里将由lamdba表示的bean的第二个[ObjectFactory]保存到了`singletonFactories`，lambda中的`getEarlyBeanReference()`方法只是遍历了[SmartInstantiationAwareBeanPostProcessor]执行相关函数对bean实例做处理后返回。

    在将bean的第二个[ObjectFactory]保存到了`singletonFactories`后，执行了`populateBean(beanName, mbd, instanceWrapper)`方法填充属性，而这一过程就会涉及到其他bean的创建，回到最开始的例子，对于beanA存在属性beanB，beanB存在属性beanA，当beanA创建过程中执行`populateBean(beanName, mbd, instanceWrapper)`注入beanB从而引起beanB的创建，而在beanB创建过程中同样会执行`populateBean(beanName, mbd, instanceWrapper)`注入beanA，当注入过程中调用了`getBean()`方法获取beanA时，会先执行`getSingleton(beanA)`，此时将能够获取到beanA的第二个[ObjectFactory]并调用其`getObject()`方法，即上面的`getEarlyBeanReference()`方法返回正在创建的beanA，此时返回的beanA属性还未注入完成。在注入beanA后beanB创建过程正常结束，此时将会回到beanA的创建过程，beanA获取到了beanB，并且beanB的beanA也注入完成，之后beanA的创建过程也可以正常结束。由于beanA和beanB都是单例的，所以即使创建过程中beanB注入的beanA是未初始化完全的，在之后beanA正常创建结束并初始化完全后beanB的beanA属性也会初始化完全。
- `populateBean()`方法执行完成后bean的属性已经都初始化完了，之后会执行`initializeBean()`方法调用各种初始化bean的方法，代码：
  ```java
  protected Object initializeBean(final String beanName, final Object bean, @Nullable RootBeanDefinition mbd) {
      if (System.getSecurityManager() != null) {
          AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
              invokeAwareMethods(beanName, bean);
              return null;
          }, getAccessControlContext());
      }
      else {
          // 如果bean实现了BeanNameAware、BeanClassLoaderAware、BeanFactoryAware等接口，则调用相关set方法
          invokeAwareMethods(beanName, bean);
      }

      Object wrappedBean = bean;
      if (mbd == null || !mbd.isSynthetic()) {
          // 调用BeanPostProcessor的postProcessBeforeInitialization方法
          wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
      }

      try {
          // 如果bean实现了InitializingBean接口，则调用bean的afterPropertiesSet方法，如果bean指定了InitMethod，则调用该方法
          invokeInitMethods(beanName, wrappedBean, mbd);
      }
      catch (Throwable ex) {
          throw new BeanCreationException(
                  (mbd != null ? mbd.getResourceDescription() : null),
                  beanName, "Invocation of init method failed", ex);
      }
      if (mbd == null || !mbd.isSynthetic()) {
          // 调用BeanPostProcessor的postProcessAfterInitialization方法
          wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
      }

      return wrappedBean;
  }
  ```
  以上是从容器获取bean的过程


[ClassPathXmlApplicationContext]: aaa
[ObjectFactory]: aaa
[AbstractFactoryBean]: aaa
[BeanExpressionResolver]: aaa
[ConversionService]: aaa
[InstantiationAwareBeanPostProcessor]: aaa
[BeanWrapper]: aaa
[BeanPostProcessor]: aaa
[PropertyAccessor]: aaa
[MergedBeanDefinitionPostProcessor]: aaa
[XmlBeanDefinitionReader]: aaa
[BeanDefinitionRegistry]: aaa
[DefaultListableBeanFactory]: aaa
[AliasRegistry]: aaa
[SimpleAliasRegistry]: aaa
[BeanDefinitionRegistry]: aaa
[BeanDefinition]: aaa
[BeanFactory]: aaa
[SingletonBeanRegistry]: aaa
[DefaultSingletonBeanRegistry]: aaa
[FactoryBeanRegistrySupport]: aaa
[FactoryBean]: aaa
[HierarchicalBeanFactory]: aaa
[ConfigurableBeanFactory]: aaa
[AbstractBeanFactory]: aaa
[AutowireCapableBeanFactory]: aaa
[AbstractAutowireCapableBeanFactory]: aaa
[ListableBeanFactory]: aaa
[ConfigurableListableBeanFactory]: aaa