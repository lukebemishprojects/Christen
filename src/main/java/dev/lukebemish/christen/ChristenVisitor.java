package dev.lukebemish.christen;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiTypeElement;
import net.neoforged.jst.api.PsiHelper;
import net.neoforged.jst.api.Replacement;
import net.neoforged.jst.api.Replacements;
import net.neoforged.srgutils.IMappingFile;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

class ChristenVisitor extends PsiRecursiveElementVisitor {
    private final IMappingFile mappings;
    private final Replacements replacements;

    private final Map<String, String> remappedImports = new HashMap<>();
    private final Map<MemberReference, MemberReference> remappedStaticImportFields = new HashMap<>();
    private final Map<MemberReference, MemberReference> remappedStaticImportMethods = new HashMap<>();
    private final Map<MemberReference, StarReferenceImportData> remappedStaticStarImportFields = new HashMap<>();
    private final Map<MemberReference, StarReferenceImportData> remappedStaticStarImportMethods = new HashMap<>();
    private final Map<String, StarImportData> remappedStarImports = new HashMap<>();

    private record MemberReference(String owner, String name) {}
    private record StarImportData(boolean[] handled, PsiImportStatementBase statement, String remappedName) {
        void handle(Replacements replacements) {
            if (handled[0]) {
                return;
            }
            handled[0] = true;
            replacements.insertBefore(statement, "import "+remappedName+";");
        }
    }
    private record StarReferenceImportData(boolean[] handled, PsiImportStaticStatement statement, String remappedOwner, String remappedName) {
        void handle(Replacements replacements) {
            if (handled[0]) {
                return;
            }
            handled[0] = true;
            replacements.insertBefore(statement, "import static "+remappedOwner+"."+remappedName+";");
        }
    }

    ChristenVisitor(IMappingFile mappings, Replacements replacements) {
        this.mappings = mappings;
        this.replacements = replacements;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiImportStatementBase importStatement) {
            handleImport(importStatement);
            return;
        } else if (element instanceof PsiTypeElement typeElement) {
            var reference = typeElement.getInnermostComponentReferenceElement();
            // TODO: WTH is going on here...
        }
        super.visitElement(element);
    }

    private void handleImport(PsiImportStatementBase importStatement) {
        var reference = importStatement.resolve();
        if (reference instanceof PsiClass psiClass) {
            var importPath = psiClass.getQualifiedName();
            if (importPath != null) {
                var remapped = formatAsBefore(mappings.remapClass(binaryName(psiClass)), psiClass);
                if (!remapped.equals(importPath)) {
                    replacements.add(new Replacement(importStatement.getTextRange(), "import "+remapped+";"));
                    remappedImports.put(importPath, remapped);
                }
            }
        } else if (reference instanceof PsiPackage psiPackage) {
            for (var psiClass : psiPackage.getClasses()) {
                var importPath = psiClass.getQualifiedName();
                if (importPath != null) {
                    var remapped = formatAsBefore(mappings.remapClass(binaryName(psiClass)), psiClass);
                    if (!remapped.equals(importPath)) {
                        remappedStarImports.put(importPath, new StarImportData(new boolean[0], importStatement, remapped));
                        replacements.add(new Replacement(importStatement.getTextRange(), ""));
                    }
                }
            }
        } else if (reference instanceof PsiField psiField) {
            var containingClass = psiField.getContainingClass();
            if (containingClass != null) {
                var originalClass = containingClass.getQualifiedName();
                if (originalClass != null) {
                    var remappedClass = formatAsBefore(mappings.remapClass(binaryName(containingClass)), containingClass);
                    var newFieldName = remapField(psiField, mappings, containingClass);
                    if (newFieldName != null || !remappedClass.equals(originalClass)) {
                        var oldMemberReference = new MemberReference(originalClass, psiField.getName());
                        var newMemberReference = new MemberReference(remappedClass, newFieldName != null ? newFieldName : psiField.getName());
                        remappedStaticImportFields.put(oldMemberReference, newMemberReference);
                        replacements.add(new Replacement(importStatement.getTextRange(), "import static "+newMemberReference.owner+"."+newMemberReference.name+";"));
                    }
                }
            }
        } else if (reference instanceof PsiMethod psiMethod) {
            var containingClass = psiMethod.getContainingClass();
            if (containingClass != null) {
                var originalClass = containingClass.getQualifiedName();
                if (originalClass != null) {
                    var remappedClass = formatAsBefore(mappings.remapClass(binaryName(containingClass)), containingClass);
                    var newMethodName = remapMethod(psiMethod, mappings, containingClass);
                    if (newMethodName != null || !remappedClass.equals(originalClass)) {
                        var oldMemberReference = new MemberReference(originalClass, psiMethod.getName());
                        var newMemberReference = new MemberReference(remappedClass, newMethodName != null ? newMethodName : psiMethod.getName());
                        remappedStaticImportMethods.put(oldMemberReference, newMemberReference);
                        replacements.add(new Replacement(importStatement.getTextRange(), "import static "+newMemberReference.owner+"."+newMemberReference.name+";"));
                    }
                }
            }
        } else if (importStatement instanceof PsiImportStaticStatement psiImportStaticStatement && importStatement.isOnDemand()) {
            var targetClass = psiImportStaticStatement.resolveTargetClass();
            if (targetClass != null) {
                var originalClass = targetClass.getQualifiedName();
                if (originalClass != null) {
                    var remappedClass = formatAsBefore(mappings.remapClass(binaryName(targetClass)), targetClass);
                    for (var method : targetClass.getAllMethods()) {
                        if (!method.hasModifier(JvmModifier.STATIC)) {
                            continue;
                        }
                        var newMethodName = remapMethod(method, mappings, targetClass);
                        if (newMethodName != null || !remappedClass.equals(originalClass)) {
                            var oldMemberReference = new MemberReference(originalClass, method.getName());
                            var newMemberReference = new StarReferenceImportData(new boolean[0], psiImportStaticStatement, remappedClass, newMethodName != null ? newMethodName : method.getName());
                            remappedStaticStarImportMethods.put(oldMemberReference, newMemberReference);
                        }
                    }
                    for (var field : targetClass.getAllFields()) {
                        if (!field.hasModifier(JvmModifier.STATIC)) {
                            continue;
                        }
                        var newMethodName = remapField(field, mappings, targetClass);
                        if (newMethodName != null || !remappedClass.equals(originalClass)) {
                            var oldMemberReference = new MemberReference(originalClass, field.getName());
                            var newMemberReference = new StarReferenceImportData(new boolean[0], psiImportStaticStatement, remappedClass, newMethodName != null ? newMethodName : field.getName());
                            remappedStaticStarImportFields.put(oldMemberReference, newMemberReference);
                        }
                    }
                    for (var inner : targetClass.getAllInnerClasses()) {
                        var qualifiedName = inner.getQualifiedName();
                        if (qualifiedName != null) {
                            var remappedInnerClass = formatAsBefore(mappings.remapClass(binaryName(inner)), inner);
                            if (!remappedInnerClass.equals(qualifiedName)) {
                                remappedStarImports.put(qualifiedName, new StarImportData(new boolean[0], psiImportStaticStatement, remappedInnerClass));
                            }
                        }
                    }
                    replacements.add(new Replacement(importStatement.getTextRange(), ""));
                }
            }
        }
    }

    private String formatAsBefore(String name, PsiClass original) {
        if (original.getContainingClass() != null) {
            var index = name.lastIndexOf('$');
            name = name.substring(0, index) + "." + name.substring(index + 1);
        }
        return name.replace('/', '.');
    }

    private static @Nullable String remapField(PsiField psiField, IMappingFile mappings, PsiClass originalClass) {
        var clazz = mappings.getClass(binaryName(originalClass));
        if (clazz == null) {
            for (var type : originalClass.getSupers()) {
                var typeQualifiedName = type.getQualifiedName();
                if (typeQualifiedName != null) {
                    if (typeQualifiedName.equals("java.lang.Object")) {
                        continue;
                    }
                    var searched = remapField(psiField, mappings, type);
                    if (searched != null) {
                        return searched;
                    }
                }
            }
            return null;
        }
        if (clazz.getField(psiField.getName()) == null) {
            return null;
        }
        return clazz.remapField(psiField.getName());
    }

    private static @Nullable String remapMethod(PsiMethod psiMethod, IMappingFile mappings, PsiClass originalClass) {
        var clazz = mappings.getClass(binaryName(originalClass));
        if (clazz == null) {
            for (var type : originalClass.getSupers()) {
                var typeQualifiedName = type.getQualifiedName();
                if (typeQualifiedName != null) {
                    if (typeQualifiedName.equals("java.lang.Object")) {
                        continue;
                    }
                    var searched = remapMethod(psiMethod, mappings, type);
                    if (searched != null) {
                        return searched;
                    }
                }
            }
            return null;
        }
        var desc = PsiHelper.getBinaryMethodSignature(psiMethod);
        if (clazz.getMethod(psiMethod.getName(), desc) == null) {
            return null;
        }
        return clazz.remapMethod(psiMethod.getName(), desc);
    }

    private static String binaryName(PsiClass psiClass) {
        StringBuilder builder = new StringBuilder();
        PsiHelper.getBinaryClassName(psiClass, builder);
        return builder.toString();
    }
}
