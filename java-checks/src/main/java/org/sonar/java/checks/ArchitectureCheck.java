/*
 * SonarQube Java
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.checks;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.utils.WildcardPattern;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.java.RspecKey;
import org.sonar.java.resolve.JavaSymbol;
import org.sonar.java.resolve.JavaType;
import org.sonar.java.resolve.ParametrizedTypeJavaType;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.InstanceOfTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.ParameterizedTypeTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TypeTree;
import org.sonar.plugins.java.api.tree.VariableTree;
import org.sonar.plugins.java.api.tree.WildcardTree;
import org.sonar.squidbridge.annotations.RuleTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Rule(key = "ArchitecturalConstraint")
@RspecKey("S1212")
@RuleTemplate
public class ArchitectureCheck extends IssuableSubscriptionVisitor {

  @RuleProperty(description = "Optional. If this property is not defined, all classes should adhere to this constraint. Ex : **.web.**")
  String fromClasses = "";

  @RuleProperty(description = "Mandatory. Ex : java.util.Vector, java.util.Hashtable, java.util.Enumeration")
  String toClasses = "";

  private WildcardPattern[] fromPatterns;
  private WildcardPattern[] toPatterns;

  private Set<String> currentIssues = new HashSet<>();

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.CLASS, Tree.Kind.ENUM, Tree.Kind.INTERFACE, Tree.Kind.ANNOTATION_TYPE);
  }

  @Override
  public void visitNode(Tree tree) {
    currentIssues.clear();
    ClassTree classTree = (ClassTree) tree;
    JavaSymbol.TypeJavaSymbol symbol = (JavaSymbol.TypeJavaSymbol) classTree.symbol();
    String fullyQualifiedName = symbol.getFullyQualifiedName();
    if (WildcardPattern.match(getFromPatterns(), fullyQualifiedName)) {
      // prevent self checks
      currentIssues.add(fullyQualifiedName);
      classTree.superInterfaces().forEach(i -> check(i.symbolType(), i, fullyQualifiedName));
      TypeTree superClass = classTree.superClass();
      if (superClass != null) {
        check(superClass.symbolType(), superClass, fullyQualifiedName);
      }
      new CheckVisitor(fullyQualifiedName).visitClassTree(classTree);
    }
  }

  private void check(Type typeToCheck, Tree reportTree, String className) {
    Type type = typeToCheck;
    while (type.isArray()) {
        type = ((Type.ArrayType) type).elementType();
    }
    String fullyQualifiedName = type.fullyQualifiedName();
    if (!currentIssues.contains(fullyQualifiedName) && WildcardPattern.match(getToPatterns(), fullyQualifiedName)) {
      reportIssue(reportTree, className + " must not use " + fullyQualifiedName);
      currentIssues.add(fullyQualifiedName);
    }
  }

  private class CheckVisitor extends BaseTreeVisitor {

    private final String className;

    CheckVisitor(String className) {
      this.className = className;
    }

    private void check(Type type, Tree reportTree) {
      ArchitectureCheck.this.check(type, reportTree, className);
    }

    @Override
    public void visitMethodInvocation(MethodInvocationTree tree) {
      super.visitMethodInvocation(tree);
      if (!tree.symbol().isUnknown()) {
        check(tree.symbol().owner().type(), tree);
      }
    }

    @Override
    public void visitMethod(MethodTree tree) {
      scan(tree.typeParameters());

      TypeTree returnType = tree.returnType();
      if(returnType != null) {
        check(returnType.symbolType(), returnType);
        scan(returnType);
      }
      scan(tree.parameters());
      tree.throwsClauses().stream().forEach(e -> check(e.symbolType(), e));
      scan(tree.block());
    }

    @Override
    public void visitVariable(VariableTree tree) {
      Type type = tree.type().symbolType();
      check(type, tree.type());
      scan(tree.type());
      scan(tree.initializer());
    }

    private void visitClassTree(ClassTree tree) {
      scan(tree.typeParameters());
      scan(tree.superClass());
      scan(tree.superInterfaces());
      scan(tree.members());
    }

    @Override
    public void visitNewClass(NewClassTree tree) {
      super.visitNewClass(tree);
      check(tree.symbolType(), tree);
    }

    @Override
    public void visitMemberSelectExpression(MemberSelectExpressionTree tree) {
      if ("class".equals(tree.identifier().name()) && ((JavaType) tree.symbolType()).isParameterized()) {
        ParametrizedTypeJavaType parametrizedTypeJavaType = (ParametrizedTypeJavaType) tree.symbolType();
        check(parametrizedTypeJavaType.substitution(parametrizedTypeJavaType.typeParameters().get(0)), tree);
      }
      if(!tree.identifier().symbol().owner().isPackageSymbol()) {
        check(tree.identifier().symbol().owner().type(), tree);
      }
      super.visitMemberSelectExpression(tree);
    }

    @Override
    public void visitInstanceOf(InstanceOfTree tree) {
      super.visitInstanceOf(tree);
      check(tree.type().symbolType(), tree.type());
    }

    @Override
    public void visitParameterizedType(ParameterizedTypeTree tree) {
      scan(tree.typeArguments());
      for (Tree typeArg : tree.typeArguments()) {
        if (typeArg.is(Tree.Kind.EXTENDS_WILDCARD, Tree.Kind.SUPER_WILDCARD, Tree.Kind.UNBOUNDED_WILDCARD)) {
          scan(typeArg);
        } else {
          check(((ExpressionTree) typeArg).symbolType(), tree);
        }
      }
    }

    @Override
    public void visitWildcard(WildcardTree tree) {
      TypeTree bound = tree.bound();
      if (bound == null) {
        return;
      }
      scan(bound);
      check(bound.symbolType(), bound);

    }

    @Override
    public void visitClass(ClassTree tree) {
      // avoid visiting subclasses
    }
  }

  private WildcardPattern[] getFromPatterns() {
    if (fromPatterns == null) {
      fromPatterns = PatternUtils.createPatterns(StringUtils.defaultIfEmpty(fromClasses, "**"));
    }
    return fromPatterns;
  }

  private WildcardPattern[] getToPatterns() {
    if (toPatterns == null) {
      toPatterns = PatternUtils.createPatterns(toClasses);
    }
    return toPatterns;
  }
}
