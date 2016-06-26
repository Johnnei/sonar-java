package org.sonar.java.checks.targets;

import java.util.regex.Pattern;
import java.io.File;

public class ArchitectureConstraint {
  int a = 1;
  Pattern pattern = Pattern.compile("*.java"); // Noncompliant [[sc=3;ec=10]] {{org.sonar.java.checks.targets.ArchitectureConstraint must not use java.util.regex.Pattern}}
  public ArchitectureConstraint() {
    Pattern.compile("*.java");
    Pattern.compile("*");
    new Object() {
      Pattern pattern = Pattern.compile("*.java"); // Noncompliant {{org.sonar.java.checks.targets.ArchitectureConstraint$1 must not use java.util.regex.Pattern}}
    };
    File file = new File("a"); // Noncompliant {{org.sonar.java.checks.targets.ArchitectureConstraint must not use java.io.File}}
    String separator = File.separator;
  }

  class A {
    Pattern pattern = Pattern.compile("*.java"); // Noncompliant {{org.sonar.java.checks.targets.ArchitectureConstraint$A must not use java.util.regex.Pattern}}
    class AA {
      Pattern pattern = Pattern.compile("*.java"); // Noncompliant {{org.sonar.java.checks.targets.ArchitectureConstraint$A$AA must not use java.util.regex.Pattern}}
      Object obj = new java.lang.Object() {
        Pattern pattern = Pattern.compile("*.java"); // Noncompliant {{org.sonar.java.checks.targets.ArchitectureConstraint$A$AA$1 must not use java.util.regex.Pattern}}
      };
    }
  }

}

enum ArchitectureConstraintEnum {
  A;
  File file = new File("a"); // Noncompliant {{org.sonar.java.checks.targets.ArchitectureConstraintEnum must not use java.io.File}}
  ArchitectureConstraintEnum() {
    Pattern.compile("*.java"); // Noncompliant {{org.sonar.java.checks.targets.ArchitectureConstraintEnum must not use java.util.regex.Pattern}}
  }
}

interface ArchitectureConstraintInterface {
  Pattern pattern = Pattern.compile("*.java"); // Noncompliant {{org.sonar.java.checks.targets.ArchitectureConstraintInterface must not use java.util.regex.Pattern}}
}

@interface ArchitectureConstraintAnnotation {
  Pattern pattern = Pattern.compile("*.java"); // Noncompliant {{org.sonar.java.checks.targets.ArchitectureConstraintAnnotation must not use java.util.regex.Pattern}}
}

interface CheckParameterized {
  java.util.List<File> files; // Noncompliant {{org.sonar.java.checks.targets.CheckParameterized must not use java.io.File}}
}

interface CheckParameterized2 {
  java.util.List<? extends File> files; // Noncompliant {{org.sonar.java.checks.targets.CheckParameterized2 must not use java.io.File}}
}

class InstanceOf {
  void foo() {
    if(foo instanceof File) { // Noncompliant {{org.sonar.java.checks.targets.InstanceOf must not use java.io.File}}
    }
  }
}
class NewClass {
  void foo() {
    foo(new File("")); // Noncompliant {{org.sonar.java.checks.targets.NewClass must not use java.io.File}}
  }
}

class ClassExpression {
  Class<?> foo() {
    return File.class; // Noncompliant {{org.sonar.java.checks.targets.ClassExpression must not use java.io.File}}
  }
}

class NestedReturnType {
  List<File> foo()  {} // Noncompliant {{org.sonar.java.checks.targets.NestedReturnType must not use java.io.File}}
}

class StaticAccess {
  void foo() {
    File.separator; // Noncompliant {{org.sonar.java.checks.targets.StaticAccess must not use java.io.File}}
  }
}

class Arrays {
  File[] files; // Noncompliant {{org.sonar.java.checks.targets.Arrays must not use java.io.File}}
}
