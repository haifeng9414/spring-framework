- [Advice]
  
  标记接口，所有advice都需要实现该接口，代码：
  ```java
  public interface Advice {

  }
  ```

- [Pointcut]

  [Pointcut]接口的作用是对[advice]的适用性进行判断
  ```java
  public interface Pointcut {
    ClassFilter getClassFilter();

    MethodMatcher getMethodMatcher();

    Pointcut TRUE = TruePointcut.INSTANCE;

  }
  ```

- [ExpressionPointcut]
  
  [Pointcut]接口的子接口，能够返回[Pointcut]的表达式，代码：
  ```java
  public interface ExpressionPointcut extends Pointcut {
    /**
     * Return the String expression for this pointcut.
     */
    @Nullable
    String getExpression();

  }
  ```

- [Adviosr]和[PointcutAdvisor]

  [Advisor]在Spring中的作用是持有一个[advice]，并且能够判断[advice]的适用性
  ```java
  public interface Advisor {
    Advice EMPTY_ADVICE = new Advice() {};

    Advice getAdvice();

    /**
     * Return whether this advice is associated with a particular instance
     * (for example, creating a mixin) or shared with all instances of
     * the advised class obtained from the same Spring bean factory.
     * <p><b>Note that this method is not currently used by the framework.</b>
     * Typical Advisor implementations always return {@code true}.
     * Use singleton/prototype bean definitions or appropriate programmatic
     * proxy creation to ensure that Advisors have the correct lifecycle model.
     * @return whether this advice is associated with a particular target instance
     */
    boolean isPerInstance();

  }
  ```

  适用性没有体现在[Advisor]接口中，因为[Advisor]的适用性判断方式不只一种，最常见的是通过[Pointcut]判断[advice]的使用性，代码：
  ```java
  public interface PointcutAdvisor extends Advisor {
    Pointcut getPointcut();
  }
  ```

- [Interceptor]和[MethodInterceptor]
  
  空接口，继承[Advice]接口，所有可以拦截JointPoint的advice都要实现该接口，如方法调用，方法抛出异常等，代码：
  ```java
  public interface Interceptor extends Advice {

  }

  ```

  [MethodInterceptor]表示方法调用的拦截
  ```java
  public interface MethodInterceptor extends Interceptor {
      Object invoke(MethodInvocation invocation) throws Throwable;
  }
  ```

- [DynamicIntroductionAdvice] 
  
  Spring AOP有个概念叫introduction（引言），introduction的意思是Spring AOP允许通过advice指定代理额外需要实现的接口，执行这些接口方法时交由advice执行，笔记[spring的scope如何使用和实现](../../容器的使用/Spring的scope如何使用和实现.md)中的[DelegatingIntroductionInterceptor]就是这种advice，[DynamicIntroductionAdvice]的作用是对advice对象上的接口进行过滤，`implementsInterface()`方法返回true的接口才会被代理，[DynamicIntroductionAdvice]接口代码：
  ```java
  public interface DynamicIntroductionAdvice extends Advice {
      boolean implementsInterface(Class<?> intf);
  }
  ```

- [IntroductionInfo]

  [IntroductionInfo]接口能够返回[DynamicIntroductionAdvice]接口中提到的introduction所指定的接口，所以通常和[DynamicIntroductionAdvice]接口同时使用，一个负责返回introduction指定的接口，一个过滤接口，代码：
  ```java
  public interface IntroductionInfo {
    Class<?>[] getInterfaces();
  }
  ```

- [IntroductionAdvisor]

  [IntroductionAdvisor]接口表示的是一个introduction advisor，除了提供[IntroductionInfo]接口和[Advisor]接口所具备的能力外，还能够对[IntroductionAdvisor]的实例所针对的代理类类型进行过滤，这也是[PointcutAdvisor]接口中提到的另一种判断[Advice]适用性的方法，[IntroductionAdvisor]接口还能验证[Advisor]中的[Advice]是否实现了所有[IntroductionInfo]接口的`getInterfaces()`方法返回的接口
  ```java
  public interface IntroductionAdvisor extends Advisor, IntroductionInfo {
    ClassFilter getClassFilter();

    /**
     * Can the advised interfaces be implemented by the introduction advice?
     * Invoked before adding an IntroductionAdvisor.
     */
    void validateInterfaces() throws IllegalArgumentException;
  }
  ```

- [IntroductionInterceptor]

  [IntroductionInterceptor]接口自己没有方法，目的是结合[MethodInterceptor]和[DynamicIntroductionAdvice]接口，代码：
  ```java
  public interface IntroductionInterceptor extends MethodInterceptor, DynamicIntroductionAdvice {

  }
  ```
  
- [AfterAdvice]等接口
 
  [AfterAdvice]是个空接口，标记after advice，类似的还有[BeforeAdvice]接口、[ThrowsAdvice]等，[AfterAdvice]代码：
  ```java
  public interface AfterAdvice extends Advice {

  }
  ```

  [AfterAdvice]有一个子接口[AfterReturningAdvice]，表示方法执行后执行的advice，代码：
  ```java
  public interface AfterReturningAdvice extends AfterAdvice {
      // 被代理方法成功返回后执行
      void afterReturning(@Nullable Object returnValue, Method method, Object[] args, @Nullable Object target) throws Throwable;

  }
  ```
  
  类似的还有[BeforeAdvice]接口的实现：[MethodBeforeAdvice]，[ThrowsAdvice]接口的实现比较特殊，[ThrowsAdvice]没有子接口，实现类只需要有以下形式的方法，当被代理方法抛出异常时就会被执行：
  - public void afterThrowing(Exception ex)
  - public void afterThrowing(RemoteException)
  - public void afterThrowing(Method method, Object[] args, Object target, Exception ex)
  - public void afterThrowing(Method method, Object[] args, Object target, ServletException ex)
    
  如果上述方法自己抛出了异常，则会覆盖被代理方法抛出的异常

- [InstantiationModelAwarePointcutAdvisor]

  [InstantiationModelAwarePointcutAdvisor]接口继承自[PointcutAdvisor]接口，添加了延迟初始化[Advice]的方法，代码：
  ```java
  public interface InstantiationModelAwarePointcutAdvisor extends PointcutAdvisor {
    /**
     * Return whether this advisor is lazily initializing its underlying advice.
     */
    boolean isLazy();

    /**
     * Return whether this advisor has already instantiated its advice.
     */
    boolean isAdviceInstantiated();
  }
  ```

  [Advice]: aaa
  [Interceptor]: aaa
  [DynamicIntroductionAdvice]: aaa
  [DelegatingIntroductionInterceptor]: aaa