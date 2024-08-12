package dev.lukebemish.christen;

import com.google.common.collect.Sets;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiRecursiveElementVisitor;
import net.neoforged.jst.api.PsiHelper;
import net.neoforged.jst.api.Replacement;
import net.neoforged.jst.api.Replacements;
import net.neoforged.srgutils.IMappingFile;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class ChristenVisitor extends PsiRecursiveElementVisitor {
    private final IMappingFile mappings;
    private final Replacements replacements;

    private final Map<String, String> remappedImports = new HashMap<>();
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
            replacements.insertAfter(statement, "import "+remappedName+";");
        }
    }
    private record StarReferenceImportData(boolean[] handled, PsiImportStaticStatement statement, String remappedOwner, String remappedName) {
        void handle(Replacements replacements) {
            if (handled[0]) {
                return;
            }
            handled[0] = true;
            replacements.insertAfter(statement, "import static "+remappedOwner+"."+remappedName+";");
        }
    }

    ChristenVisitor(IMappingFile mappings, Replacements replacements) {
        this.mappings = mappings;
        this.replacements = replacements;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        switch (element) {
            case PsiImportStatementBase importStatement -> {
                handleImport(importStatement);
                return;
            }
            case PsiJavaCodeReferenceElement reference -> {
                var resolved = reference.resolve();
                if (reference.getQualifier() instanceof PsiElement qualifier) {
                    visitElement(qualifier);
                }
                if (reference.getParameterList() != null) {
                    for (var type : reference.getParameterList().getTypeParameterElements()) {
                        visitElement(type);
                    }
                }
                switch (resolved) {
                    case PsiField field -> {
                        var fieldName = reference.getReferenceName();
                        var remappedName = remapField(field, mappings, field.getContainingClass());
                        if (remappedName != null && !remappedName.equals(fieldName)) {
                            replacements.add(new Replacement(reference.getReferenceNameElement().getTextRange(), remappedName));
                            var originalReference = new MemberReference(field.getContainingClass().getQualifiedName(), fieldName);
                            var starImport = remappedStaticStarImportFields.get(originalReference);
                            if (starImport != null) {
                                starImport.handle(replacements);
                            }
                        }
                    }
                    case PsiMethod method -> {
                        var methodName = reference.getReferenceName();
                        var remappedName = remapMethod(method, mappings, method.getContainingClass());
                        if (remappedName != null && !remappedName.equals(methodName)) {
                            replacements.add(new Replacement(reference.getReferenceNameElement().getTextRange(), remappedName));
                            var originalReference = new MemberReference(method.getContainingClass().getQualifiedName(), methodName);
                            var starImport = remappedStaticStarImportMethods.get(originalReference);
                            if (starImport != null) {
                                starImport.handle(replacements);
                            }
                        }
                    }
                    case PsiClass psiClass -> {
                        remapTypeAtReference(reference, psiClass);
                    }
                    case null -> {}
                    default -> {}
                }
                return;
            }
            default -> {

            }
        }
        super.visitElement(element);
    }

    private boolean remapTypeAtReference(PsiJavaCodeReferenceElement classReference, PsiClass psiClass) {
        var qualifier = classReference.getQualifier();
        if (qualifier instanceof PsiJavaCodeReferenceElement referenceElement && referenceElement.resolve() instanceof PsiClass qualifierClass) {
            // qualifier has already been remapped
            var remappedName = formatAsBefore(mappings.remapClass(binaryName(psiClass)), psiClass);
            var lastPiece = remappedName.substring(remappedName.lastIndexOf('.')+1);
            replacements.add(new Replacement(classReference.getReferenceNameElement().getTextRange(), lastPiece));
            return true;
        }
        if (psiClass != null) {
            var originalClass = psiClass.getQualifiedName();
            if (checkImportForPrefix(classReference, originalClass)) return true;
            if (mappings.getClass(binaryName(psiClass)) == null) {
                return false;
            }
            var remappedClass = formatAsBefore(mappings.remapClass(binaryName(psiClass)), psiClass);
            int start;
            int end = classReference.getReferenceNameElement().getTextRange().getEndOffset();
            if (classReference.getQualifier() != null) {
                start = classReference.getQualifier().getTextRange().getStartOffset();
            } else {
                start = classReference.getTextRange().getStartOffset();
            }
            replacements.add(new Replacement(new TextRange(start, end), remappedClass));
            return true;
        }
        return false;
    }

    private boolean checkImportForPrefix(PsiJavaCodeReferenceElement referenceElement, String originalClass) {
        var remappedImport = remappedImports.get(originalClass);
        if (remappedImport != null) {
            var simpleName = remappedImport.substring(remappedImport.lastIndexOf('.')+1);
            replacements.add(new Replacement(referenceElement.getReferenceNameElement().getTextRange(), simpleName));
            return true;
        }
        var remappedStarImport = remappedStarImports.get(originalClass);
        if (remappedStarImport != null) {
            var simpleName = remappedStarImport.remappedName().substring(remappedStarImport.remappedName().lastIndexOf('.')+1);
            replacements.add(new Replacement(referenceElement.getReferenceNameElement().getTextRange(), simpleName));
            remappedStarImport.handle(replacements);
            return true;
        }
        return false;
    }

    private void handleImport(PsiImportStatementBase importStatement) {
        var reference = importStatement.resolve();
        switch (reference) {
            case PsiClass psiClass when !(importStatement instanceof PsiImportStaticStatement) -> {
                var importPath = psiClass.getQualifiedName();
                if (importPath != null) {
                    var remapped = formatAsBefore(mappings.remapClass(binaryName(psiClass)), psiClass);
                    if (!remapped.equals(importPath)) {
                        replacements.add(new Replacement(importStatement.getTextRange(), "import "+remapped+";"));
                        remappedImports.put(importPath, remapped);
                    }
                }
            }
            case PsiPackage psiPackage -> {
                for (var psiClass : psiPackage.getClasses()) {
                    var importPath = psiClass.getQualifiedName();
                    if (importPath != null) {
                        var remapped = formatAsBefore(mappings.remapClass(binaryName(psiClass)), psiClass);
                        if (!remapped.equals(importPath)) {
                            remappedStarImports.put(importPath, new StarImportData(new boolean[1], importStatement, remapped));
                        }
                    }
                }
            }
            case PsiField psiField -> {
                var containingClass = psiField.getContainingClass();
                if (containingClass != null) {
                    var originalClass = containingClass.getQualifiedName();
                    if (originalClass != null) {
                        var remappedClass = formatAsBefore(mappings.remapClass(binaryName(containingClass)), containingClass);
                        var newFieldName = remapField(psiField, mappings, containingClass);
                        if (newFieldName != null || !remappedClass.equals(originalClass)) {
                            var newMemberReference = new MemberReference(remappedClass, newFieldName != null ? newFieldName : psiField.getName());
                            replacements.add(new Replacement(importStatement.getTextRange(), "import static "+newMemberReference.owner+"."+newMemberReference.name+";"));
                        }
                    }
                }
            }
            case PsiMethod psiMethod -> {
                var containingClass = psiMethod.getContainingClass();
                if (containingClass != null) {
                    var originalClass = containingClass.getQualifiedName();
                    if (originalClass != null) {
                        var remappedClass = formatAsBefore(mappings.remapClass(binaryName(containingClass)), containingClass);
                        var newMethodName = remapMethod(psiMethod, mappings, containingClass);
                        if (newMethodName != null || !remappedClass.equals(originalClass)) {
                            var newMemberReference = new MemberReference(remappedClass, newMethodName != null ? newMethodName : psiMethod.getName());
                            replacements.add(new Replacement(importStatement.getTextRange(), "import static "+newMemberReference.owner+"."+newMemberReference.name+";"));
                        }
                    }
                }
            }
            case null -> {}
            default -> {
                if (importStatement instanceof PsiImportStaticStatement psiImportStaticStatement && importStatement.isOnDemand()) {
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
                                    var newMemberReference = new StarReferenceImportData(new boolean[1], psiImportStaticStatement, remappedClass, newMethodName != null ? newMethodName : method.getName());
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
                                    var newMemberReference = new StarReferenceImportData(new boolean[1], psiImportStaticStatement, remappedClass, newMethodName != null ? newMethodName : field.getName());
                                    remappedStaticStarImportFields.put(oldMemberReference, newMemberReference);
                                }
                            }
                            for (var inner : targetClass.getAllInnerClasses()) {
                                var qualifiedName = inner.getQualifiedName();
                                if (qualifiedName != null) {
                                    var remappedInnerClass = formatAsBefore(mappings.remapClass(binaryName(inner)), inner);
                                    if (!remappedInnerClass.equals(qualifiedName)) {
                                        remappedStarImports.put(qualifiedName, new StarImportData(new boolean[1], psiImportStaticStatement, remappedInnerClass));
                                    }
                                }
                            }
                        }
                    }
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

    private final Set<String> notMappedClasses = Sets.newConcurrentHashSet();

    private @Nullable String remapField(PsiField psiField, IMappingFile mappings, PsiClass originalClass) {
        if (notMappedClasses.contains(binaryName(originalClass))) {
            return null;
        }
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
            notMappedClasses.add(binaryName(originalClass));
            return null;
        }
        if (clazz.getField(psiField.getName()) == null) {
            return null;
        }
        return clazz.remapField(psiField.getName());
    }

    private @Nullable String remapMethod(PsiMethod psiMethod, IMappingFile mappings, PsiClass originalClass) {
        if (notMappedClasses.contains(binaryName(originalClass))) {
            return null;
        }
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
            notMappedClasses.add(binaryName(originalClass));
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
