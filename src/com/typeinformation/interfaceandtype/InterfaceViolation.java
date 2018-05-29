package com.typeinformation.interfaceandtype;
/**
 * 接口与类型信息 P346
 * @author bfq
 *
 */
class B implements A {
	@Override
	public void f() {}
	public void g(){}
}
public class InterfaceViolation {

	public static void main(String[] args) {
		A a = new B();
		a.f();
		System.out.println(a.getClass().getName());
		if(a instanceof B) {
			B b = (B) a;
			b.g();
		}
	}

}
