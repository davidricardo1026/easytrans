package io.github.easytrans.processor;

import io.github.easytrans.core.annotation.Translatable;
import io.github.easytrans.core.annotation.TranslateField;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

@SupportedAnnotationTypes("io.github.easytrans.core.annotation.Translatable")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedOptions({
        "easytrans.generated.package",
        "easytrans.generated.package.suffix",
        "easytrans.generated.bridge.package",
        "easytrans.generated.bridge.package.suffix",
        "easytrans.enable.spring"
})
public class EasyTransProcessor extends AbstractProcessor {

    private String optRegistryPkg;
    private String optRegistrySuffix;
    private String optBridgePkg;
    private String optBridgeSuffix;
    private boolean enableSpring;

    private Elements elements;
    private Messager messager;
    private Filer filer;

    private TypeMirror collectionTypeMirror;
    private TypeMirror mapTypeMirror;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elements = processingEnv.getElementUtils();
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();

        Map<String, String> options = processingEnv.getOptions();

        // 1. Registry 全包名限制（最高优先级）
        this.optRegistryPkg = options.get("easytrans.generated.package");

        // 2. Registry 后缀（默认 "generated.registry"）
        this.optRegistrySuffix = options.getOrDefault("easytrans.generated.package.suffix", "generated.registry");

        // 3. Bridge 全包名限制（最高优先级，支持 mapper.package 别名进行后向兼容）
        this.optBridgePkg = options.containsKey("easytrans.generated.bridge.package") ?
                options.get("easytrans.generated.bridge.package") : options.get("easytrans.generated.mapper.package");

        // 4. Bridge 后缀（默认 "generated"，支持 mapper.package.suffix 别名进行后向兼容）
        this.optBridgeSuffix = options.containsKey("easytrans.generated.bridge.package.suffix") ?
                options.get("easytrans.generated.bridge.package.suffix") :
                options.getOrDefault("easytrans.generated.mapper.package.suffix", "generated");

        // 5. Spring 环境支持开关（默认 true）
        this.enableSpring = Boolean.parseBoolean(options.getOrDefault("easytrans.enable.spring", "true"));

        TypeElement colEl = elements.getTypeElement("java.util.Collection");
        this.collectionTypeMirror = colEl != null ? processingEnv.getTypeUtils().erasure(colEl.asType()) : null;
        TypeElement mapEl = elements.getTypeElement("java.util.Map");
        this.mapTypeMirror = mapEl != null ? processingEnv.getTypeUtils().erasure(mapEl.asType()) : null;
    }

    private String getBridgePackageName(String entityClassName) {
        if (optBridgePkg != null && !optBridgePkg.isEmpty()) {
            return optBridgePkg;
        }
        int lastDot = entityClassName.lastIndexOf('.');
        String basePkg = lastDot >= 0 ? entityClassName.substring(0, lastDot) : "";
        return basePkg.isEmpty() ? optBridgeSuffix : basePkg + "." + optBridgeSuffix;
    }

    private String getRegistryPackageName(List<EntityModel> models) {
        if (optRegistryPkg != null && !optRegistryPkg.isEmpty()) {
            return optRegistryPkg;
        }
        if (models == null || models.isEmpty()) {
            return "io.github.easytrans.generated.registry";
        }
        String entityClassName = models.get(0).entityClassName;
        int lastDot = entityClassName.lastIndexOf('.');
        String basePkg = lastDot >= 0 ? entityClassName.substring(0, lastDot) : "";
        return basePkg.isEmpty() ? optRegistrySuffix : basePkg + "." + optRegistrySuffix;
    }

    private String getBridgeFullyQualifiedName(TypeElement element) {
        String entityClassName = element.getQualifiedName().toString();
        String pkg = getBridgePackageName(entityClassName);
        String simpleName = element.getSimpleName().toString();
        return pkg + "." + simpleName + "_TranslationBridge";
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        List<EntityModel> models = new ArrayList<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(Translatable.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                continue;
            }
            try {
                models.add(analyzeEntity((TypeElement) element));
            } catch (IllegalStateException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, ex.getMessage(), element);
            }
        }

        if (models.isEmpty()) {
            return false;
        }

        for (EntityModel model : models) {
            writeBridge(model);
        }
        writeTranslationRegistry(models);

        return false;
    }

    private EntityModel analyzeEntity(TypeElement element) {
        EntityModel model = new EntityModel();
        model.entityClassName = element.getQualifiedName().toString();
        model.entitySimpleName = element.getSimpleName().toString();
        model.bridgeSimpleName = model.entitySimpleName + "_TranslationBridge";
        model.packageName = getBridgePackageName(model.entityClassName);

        Map<String, VariableElement> allFields = fieldMap(element);

        for (VariableElement field : ElementFilter.fieldsIn(element.getEnclosedElements())) {
            TranslateField translate = field.getAnnotation(TranslateField.class);
            if (translate != null) {
                String targetFieldName = field.getSimpleName().toString();
                String sourceFieldName = translate.source();
                if (!allFields.containsKey(sourceFieldName)) {
                    throw new IllegalStateException("类: " + model.entityClassName + " 声明了翻译字段: " + targetFieldName + "，但找不到 source 关联字段: " + sourceFieldName);
                }
                TranslateFieldModel tf = new TranslateFieldModel();
                tf.targetField = targetFieldName;
                tf.sourceField = sourceFieldName;
                tf.type = translate.type();
                tf.sourceGetter = getter(sourceFieldName);
                tf.targetSetter = "set" + capitalize(targetFieldName);
                model.translateFields.add(tf);
                continue;
            }

            String fieldName = field.getSimpleName().toString();
            if (isTranslatable(field.asType())) {
                NestedFieldModel nested = new NestedFieldModel();
                nested.fieldName = fieldName;
                nested.fieldType = field.asType();
                nested.getterName = getter(fieldName);
                model.nestedFields.add(nested);
            }
        }
        return model;
    }

    private void writeBridge(EntityModel model) {
        try {
            StringBuilder body = new StringBuilder();
            body.append("package ").append(model.packageName).append(";\n\n");
            body.append("import io.github.easytrans.core.context.TranslationContext;\n");
            body.append("import io.github.easytrans.core.bridge.BaseTranslationBridge;\n");
            body.append("import ").append(model.entityClassName).append(";\n");
            if (enableSpring) {
                body.append("import org.springframework.stereotype.Component;\n");
            }
            body.append("import java.util.List;\n\n");

            if (enableSpring) {
                String beanName = "easytransBridge_" + model.packageName.replace('.',
                                                                                 '_') + "_" + model.bridgeSimpleName;
                body.append("@Component(\"").append(beanName).append("\")\n");
            }
            body.append("public class ").append(model.bridgeSimpleName)
                    .append(" implements BaseTranslationBridge<").append(model.entitySimpleName).append("> {\n\n");

            body.append("    @Override\n");
            body.append("    public void extractAllIds(List<").append(model.entitySimpleName).append(
                    "> sources, TranslationContext context) {\n");
            body.append("        extractIds(sources, context);\n");
            body.append("    }\n\n");

            body.append("    @Override\n");
            body.append("    public void writeBack(List<").append(model.entitySimpleName).append(
                    "> sources, TranslationContext context) {\n");
            body.append("        fillTranslations(sources, context);\n");
            body.append("    }\n\n");

            body.append("    public static void extractIds(List<").append(model.entitySimpleName).append(
                    "> sources, TranslationContext context) {\n");
            body.append("        if (sources == null) return;\n");
            body.append("        for (").append(model.entitySimpleName).append(" item : sources) {\n");
            body.append("            extractIds(item, context);\n");
            body.append("        }\n");
            body.append("    }\n\n");

            body.append("    public static void extractIds(").append(model.entitySimpleName).append(
                    " item, TranslationContext context) {\n");
            body.append("        if (item == null) return;\n");
            for (TranslateFieldModel tf : model.translateFields) {
                body.append("        if (item.").append(tf.sourceGetter).append("() != null) {\n");
                body.append("            context.collectId(\"").append(tf.type).append("\", item.").append(tf.sourceGetter).append(
                        "());\n");
                body.append("        }\n");
            }
            for (NestedFieldModel nested : model.nestedFields) {
                String itemExpr = "item." + nested.getterName + "()";
                String extractionCode = generateRecursiveExtract(nested.fieldType, itemExpr, 1, "        ");
                body.append(extractionCode);
            }
            body.append("    }\n\n");

            body.append("    public static void fillTranslations(List<").append(model.entitySimpleName).append(
                    "> sources, TranslationContext context) {\n");
            body.append("        if (sources == null) return;\n");
            body.append("        for (").append(model.entitySimpleName).append(" item : sources) {\n");
            body.append("            fillTranslations(item, context);\n");
            body.append("        }\n");
            body.append("    }\n\n");

            body.append("    public static void fillTranslations(").append(model.entitySimpleName).append(
                    " item, TranslationContext context) {\n");
            body.append("        if (item == null) return;\n");
            for (TranslateFieldModel tf : model.translateFields) {
                body.append("        if (item.").append(tf.sourceGetter).append("() != null) {\n");
                body.append("            item.").append(tf.targetSetter).append("(context.getName(\"").append(tf.type).append(
                        "\", item.").append(tf.sourceGetter).append("()));\n");
                body.append("        }\n");
            }
            for (NestedFieldModel nested : model.nestedFields) {
                String itemExpr = "item." + nested.getterName + "()";
                String writeBackCode = generateRecursiveWriteBack(nested.fieldType, itemExpr, 1, "        ");
                body.append(writeBackCode);
            }
            body.append("    }\n");

            body.append("}\n");

            writeJava(model.packageName + "." + model.bridgeSimpleName, body.toString());
        } catch (IOException e) {
            throw new IllegalStateException("生成 TranslationBridge 失败: " + model.bridgeSimpleName, e);
        }
    }

    private void writeTranslationRegistry(List<EntityModel> models) {
        try {
            String registryPkg = getRegistryPackageName(models);
            StringBuilder body = new StringBuilder();
            body.append("package ").append(registryPkg).append(";\n\n");
            body.append("import io.github.easytrans.core.bridge.BaseTranslationBridge;\n");
            body.append("import io.github.easytrans.core.registry.TranslationRegistry;\n");
            for (EntityModel model : models) {
                body.append("import ").append(model.entityClassName).append(";\n");
                body.append("import ").append(model.packageName).append(".").append(model.bridgeSimpleName).append(";\n");
            }
            if (enableSpring) {
                body.append("import org.springframework.stereotype.Component;\n\n");
            } else {
                body.append("\n");
            }
            body.append("import java.util.HashMap;\n");
            body.append("import java.util.Map;\n\n");
            if (enableSpring) {
                String beanName = "easytransRegistry_" + registryPkg.replace('.', '_');
                body.append("@Component(\"").append(beanName).append("\")\n");
            }
            body.append("public class GeneratedTranslationRegistry implements TranslationRegistry {\n\n");
            body.append("    private final Map<Class<?>, BaseTranslationBridge<?>> byEntityClass;\n\n");

            if (enableSpring) {
                body.append("    public GeneratedTranslationRegistry(");
                body.append(String.join(", ", models.stream()
                        .map(m -> m.bridgeSimpleName + " " + toVarName(m.bridgeSimpleName)).toList()));
                body.append(") {\n");
                body.append("        Map<Class<?>, BaseTranslationBridge<?>> map = new HashMap<>();\n");
                for (EntityModel model : models) {
                    String var = toVarName(model.bridgeSimpleName);
                    body.append("        map.put(").append(model.entitySimpleName).append(".class, ").append(var).append(
                            ");\n");
                }
                body.append("        this.byEntityClass = Map.copyOf(map);\n");
                body.append("    }\n\n");
            } else {
                body.append("    public GeneratedTranslationRegistry() {\n");
                body.append("        Map<Class<?>, BaseTranslationBridge<?>> map = new HashMap<>();\n");
                for (EntityModel model : models) {
                    body.append("        map.put(").append(model.entitySimpleName).append(".class, new ").append(model.packageName).append(
                            ".").append(model.bridgeSimpleName).append("());\n");
                }
                body.append("        this.byEntityClass = Map.copyOf(map);\n");
                body.append("    }\n\n");
            }
            body.append("    @Override\n");
            body.append("    public BaseTranslationBridge<?> findBridgeByEntityClass(Class<?> entityClass) {\n");
            body.append("        return byEntityClass.get(entityClass);\n");
            body.append("    }\n");
            body.append("}\n");

            writeJava(registryPkg + ".GeneratedTranslationRegistry", body.toString());
        } catch (IOException e) {
            throw new IllegalStateException("生成 TranslationRegistry 失败", e);
        }
    }

    private void writeJava(String className, String content) throws IOException {
        JavaFileObject file = filer.createSourceFile(className);
        try (Writer writer = file.openWriter()) {
            writer.write(content);
        }
    }

    private Map<String, VariableElement> fieldMap(TypeElement typeElement) {
        Map<String, VariableElement> map = new LinkedHashMap<>();
        for (VariableElement field : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
            map.put(field.getSimpleName().toString(), field);
        }
        return map;
    }

    private String toVarName(String className) {
        String simple = simpleName(className);
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    private String simpleName(String className) {
        int idx = className.lastIndexOf('.');
        return idx >= 0 ? className.substring(idx + 1) : className;
    }

    private String getter(String field) {
        return "get" + capitalize(field);
    }

    private String capitalize(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private boolean isTranslatable(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        if (element.getAnnotation(Translatable.class) != null) {
            return true;
        }
        if (isCollection(type)) {
            if (!declared.getTypeArguments().isEmpty()) {
                return isTranslatable(declared.getTypeArguments().get(0));
            }
        }
        if (isMap(type)) {
            for (TypeMirror arg : declared.getTypeArguments()) {
                if (isTranslatable(arg)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCollection(TypeMirror type) {
        if (collectionTypeMirror == null) return false;
        return processingEnv.getTypeUtils().isSubtype(
                processingEnv.getTypeUtils().erasure(type),
                collectionTypeMirror
        );
    }

    private boolean isMap(TypeMirror type) {
        if (mapTypeMirror == null) return false;
        return processingEnv.getTypeUtils().isSubtype(
                processingEnv.getTypeUtils().erasure(type),
                mapTypeMirror
        );
    }

    private String generateRecursiveExtract(TypeMirror type,
                                            String expr,
                                            int depth,
                                            String indent) {
        if (type.getKind() != TypeKind.DECLARED) {
            return "";
        }
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();

        if (element.getAnnotation(Translatable.class) != null) {
            String bridgeClassName = getBridgeFullyQualifiedName(element);
            return indent + "if (" + expr + " != null) {\n" +
                    indent + "    " + bridgeClassName + ".extractIds(java.util.Collections.singletonList(" + expr + "), context);\n" +
                    indent + "}\n";
        }

        if (isCollection(type)) {
            String varName = "item" + depth;
            String itemType = declared.getTypeArguments().get(0).toString();
            StringBuilder sb = new StringBuilder();
            sb.append(indent).append("if (").append(expr).append(" != null) {\n");
            sb.append(indent).append("    for (").append(itemType).append(" ").append(varName).append(" : ").append(expr).append(
                    ") {\n");
            sb.append(generateRecursiveExtract(declared.getTypeArguments().get(0),
                                               varName,
                                               depth + 1,
                                               indent + "        "));
            sb.append(indent).append("    }\n");
            sb.append(indent).append("}\n");
            return sb.toString();
        }

        if (isMap(type)) {
            String entryVar = "entry" + depth;
            String keyType = declared.getTypeArguments().get(0).toString();
            String valueType = declared.getTypeArguments().get(1).toString();

            StringBuilder sb = new StringBuilder();
            sb.append(indent).append("if (").append(expr).append(" != null) {\n");
            sb.append(indent).append("    for (java.util.Map.Entry<").append(keyType).append(", ").append(valueType).append(
                    "> ").append(entryVar).append(" : ").append(expr).append(".entrySet()) {\n");
            sb.append(indent).append("        if (").append(entryVar).append(" != null) {\n");

            if (isTranslatable(declared.getTypeArguments().get(0))) {
                sb.append(generateRecursiveExtract(declared.getTypeArguments().get(0),
                                                   entryVar + ".getKey()",
                                                   depth + 1,
                                                   indent + "            "));
            }

            if (isTranslatable(declared.getTypeArguments().get(1))) {
                sb.append(generateRecursiveExtract(declared.getTypeArguments().get(1),
                                                   entryVar + ".getValue()",
                                                   depth + 1,
                                                   indent + "            "));
            }

            sb.append(indent).append("        }\n");
            sb.append(indent).append("    }\n");
            sb.append(indent).append("}\n");
            return sb.toString();
        }

        return "";
    }

    private String generateRecursiveWriteBack(TypeMirror type,
                                              String expr,
                                              int depth,
                                              String indent) {
        if (type.getKind() != TypeKind.DECLARED) {
            return "";
        }
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();

        if (element.getAnnotation(Translatable.class) != null) {
            String bridgeClassName = getBridgeFullyQualifiedName(element);
            return indent + "if (" + expr + " != null) {\n" +
                    indent + "    " + bridgeClassName + ".fillTranslations(java.util.Collections.singletonList(" + expr + "), context);\n" +
                    indent + "}\n";
        }

        if (isCollection(type)) {
            String varName = "item" + depth;
            String itemType = declared.getTypeArguments().get(0).toString();
            StringBuilder sb = new StringBuilder();
            sb.append(indent).append("if (").append(expr).append(" != null) {\n");
            sb.append(indent).append("    for (").append(itemType).append(" ").append(varName).append(" : ").append(expr).append(
                    ") {\n");
            sb.append(generateRecursiveWriteBack(declared.getTypeArguments().get(0),
                                                 varName,
                                                 depth + 1,
                                                 indent + "        "));
            sb.append(indent).append("    }\n");
            sb.append(indent).append("}\n");
            return sb.toString();
        }

        if (isMap(type)) {
            String entryVar = "entry" + depth;
            String keyType = declared.getTypeArguments().get(0).toString();
            String valueType = declared.getTypeArguments().get(1).toString();

            StringBuilder sb = new StringBuilder();
            sb.append(indent).append("if (").append(expr).append(" != null) {\n");
            sb.append(indent).append("    for (java.util.Map.Entry<").append(keyType).append(", ").append(valueType).append(
                    "> ").append(entryVar).append(" : ").append(expr).append(".entrySet()) {\n");
            sb.append(indent).append("        if (").append(entryVar).append(" != null) {\n");

            if (isTranslatable(declared.getTypeArguments().get(0))) {
                sb.append(generateRecursiveWriteBack(declared.getTypeArguments().get(0),
                                                     entryVar + ".getKey()",
                                                     depth + 1,
                                                     indent + "            "));
            }

            if (isTranslatable(declared.getTypeArguments().get(1))) {
                sb.append(generateRecursiveWriteBack(declared.getTypeArguments().get(1),
                                                     entryVar + ".getValue()",
                                                     depth + 1,
                                                     indent + "            "));
            }

            sb.append(indent).append("        }\n");
            sb.append(indent).append("    }\n");
            sb.append(indent).append("}\n");
            return sb.toString();
        }

        return "";
    }

    private static final class EntityModel {
        String entityClassName;
        String entitySimpleName;
        String bridgeSimpleName;
        String packageName;
        final List<TranslateFieldModel> translateFields = new ArrayList<>();
        final List<NestedFieldModel> nestedFields = new ArrayList<>();
    }

    private static final class TranslateFieldModel {
        String targetField;
        String sourceField;
        String type;
        String sourceGetter;
        String targetSetter;
    }

    private static final class NestedFieldModel {
        String fieldName;
        TypeMirror fieldType;
        String getterName;
    }
}
