package io.github.easytrans.processor;

import io.github.easytrans.core.annotation.TranslateField;
import io.github.easytrans.core.annotation.TranslateFrom;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

@SupportedAnnotationTypes("io.github.easytrans.core.annotation.TranslateFrom")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedOptions({
        "easytrans.generated.package",
        "easytrans.generated.mapper.package",
        "easytrans.generated.package.suffix",
        "easytrans.generated.mapper.package.suffix",
        "easytrans.enable.spring"
})
public class EasyTransProcessor extends AbstractProcessor {

    private String optRegistryPkg;
    private String optMapperPkg;
    private String optRegistrySuffix;
    private String optMapperSuffix;
    private boolean enableSpring;

    private Elements elements;
    private Messager messager;
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elements = processingEnv.getElementUtils();
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();

        Map<String, String> options = processingEnv.getOptions();
        this.optRegistryPkg = options.get("easytrans.generated.package");
        this.optMapperPkg = options.get("easytrans.generated.mapper.package");
        this.optRegistrySuffix = options.getOrDefault("easytrans.generated.package.suffix", "generated");
        this.optMapperSuffix = options.getOrDefault("easytrans.generated.mapper.package.suffix", "generated.mapper");
        this.enableSpring = Boolean.parseBoolean(options.getOrDefault("easytrans.enable.spring", "true"));
    }

    private String getMapperPackageName(String targetClassName) {
        if (optMapperPkg != null && !optMapperPkg.isEmpty()) {
            return optMapperPkg;
        }
        int lastDot = targetClassName.lastIndexOf('.');
        String voPkg = lastDot >= 0 ? targetClassName.substring(0, lastDot) : "";
        return voPkg.isEmpty() ? optMapperSuffix : voPkg + "." + optMapperSuffix;
    }

    private String getRegistryPackageName(List<PairModel> pairs) {
        if (optRegistryPkg != null && !optRegistryPkg.isEmpty()) {
            return optRegistryPkg;
        }
        if (pairs == null || pairs.isEmpty()) {
            return "io.github.easytrans.generated";
        }
        String targetClassName = pairs.get(0).targetClassName;
        int lastDot = targetClassName.lastIndexOf('.');
        String voPkg = lastDot >= 0 ? targetClassName.substring(0, lastDot) : "";
        return voPkg.isEmpty() ? optRegistrySuffix : voPkg + "." + optRegistrySuffix;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        List<PairModel> pairs = new ArrayList<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(TranslateFrom.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                continue;
            }
            try {
                pairs.addAll(analyzePair((TypeElement) element));
            } catch (IllegalStateException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, ex.getMessage(), element);
            }
        }

        if (pairs.isEmpty()) {
            return false;
        }

        for (PairModel pair : pairs) {
            writeMapStructMapper(pair);
            writeMapperBridge(pair);
        }
        writeTranslationRegistry(pairs);

        return false;
    }

    private List<PairModel> analyzePair(TypeElement targetElement) {
        if (targetElement.getAnnotation(TranslateFrom.class) == null) {
            throw new IllegalStateException("@TranslateFrom 缺失");
        }
        String targetClassName = targetElement.getQualifiedName().toString();
        List<String> sourceClassNames = resolveSourceClassNames(targetElement);

        List<PairModel> models = new ArrayList<>();

        for (String sourceClassName : sourceClassNames) {
            TypeElement sourceElement = elements.getTypeElement(sourceClassName);
            if (sourceElement == null) {
                throw new IllegalStateException("找不到 Source 类型: " + sourceClassName);
            }

            PairModel model = new PairModel();
            model.targetClassName = targetClassName;
            model.sourceClassName = sourceClassName;
            model.targetSimpleName = targetElement.getSimpleName().toString();
            model.sourceSimpleName = sourceElement.getSimpleName().toString();
            model.mapperPackageName = getMapperPackageName(targetClassName);

            model.mapperSimpleName = model.sourceSimpleName + "To" + model.targetSimpleName + "AutoMapper";
            model.bridgeSimpleName = model.mapperSimpleName + "Bridge";

            Map<String, VariableElement> sourceFields = fieldMap(sourceElement);
            for (VariableElement targetField : ElementFilter.fieldsIn(targetElement.getEnclosedElements())) {
                TranslateField translate = targetField.getAnnotation(TranslateField.class);
                if (translate != null) {
                    if (!sourceFields.containsKey(translate.source())) {
                        throw new IllegalStateException("Source: " + sourceClassName + " 缺少字段: " + translate.source());
                    }
                    TranslateFieldModel tf = new TranslateFieldModel();
                    tf.targetField = targetField.getSimpleName().toString();
                    tf.sourceField = translate.source();
                    tf.type = translate.type();
                    tf.sourceGetter = getter(translate.source());
                    model.translateFields.add(tf);
                    continue;
                }
                NestedFieldModel nested = analyzeNestedField(targetField, sourceFields);
                if (nested != null) {
                    model.nestedFields.add(nested);
                }
            }
            models.add(model);
        }
        return models;
    }

    private NestedFieldModel analyzeNestedField(VariableElement targetField,
                                                Map<String, VariableElement> sourceFields) {
        TypeMirror targetFieldType = targetField.asType();
        if (targetFieldType.getKind() != TypeKind.DECLARED) {
            return null;
        }
        DeclaredType declared = (DeclaredType) targetFieldType;
        if (!declared.asElement().toString().equals("java.util.List")) {
            return null;
        }
        if (declared.getTypeArguments().isEmpty()) {
            return null;
        }
        TypeMirror arg = declared.getTypeArguments().get(0);
        if (arg.getKind() != TypeKind.DECLARED) {
            return null;
        }
        Element nestedTargetElement = ((DeclaredType) arg).asElement();
        if (nestedTargetElement.getAnnotation(TranslateFrom.class) == null) {
            return null;
        }
        String fieldName = targetField.getSimpleName().toString();
        if (!sourceFields.containsKey(fieldName)) {
            throw new IllegalStateException("Source 缺少嵌套字段: " + fieldName);
        }

        NestedFieldModel nested = new NestedFieldModel();
        nested.fieldName = fieldName;

        VariableElement sourceField = sourceFields.get(fieldName);
        TypeMirror sourceFieldType = sourceField.asType();
        String detectedSourceItemClassName = null;
        if (sourceFieldType.getKind() == TypeKind.DECLARED) {
            DeclaredType sourceDeclared = (DeclaredType) sourceFieldType;
            if (sourceDeclared.asElement().toString().equals("java.util.List") && !sourceDeclared.getTypeArguments().isEmpty()) {
                TypeMirror sourceArg = sourceDeclared.getTypeArguments().get(0);
                if (sourceArg.getKind() == TypeKind.DECLARED) {
                    detectedSourceItemClassName = ((DeclaredType) sourceArg).toString();
                }
            }
        }

        List<String> nestedSourceNames = resolveSourceClassNames(nestedTargetElement);
        if (nestedSourceNames.isEmpty()) {
            return null;
        }

        if (detectedSourceItemClassName != null && nestedSourceNames.contains(detectedSourceItemClassName)) {
            nested.sourceItemClassName = detectedSourceItemClassName;
        } else {
            nested.sourceItemClassName = nestedSourceNames.get(0);
        }

        nested.nestedTargetClassName = ((TypeElement) nestedTargetElement).getQualifiedName().toString();
        nested.sourceItemSimpleName = elements.getTypeElement(nested.sourceItemClassName).getSimpleName().toString();
        nested.nestedMapperSimpleName = nested.sourceItemSimpleName + "To" + nestedTargetElement.getSimpleName().toString() + "AutoMapper";
        nested.nestedMapperClassName = getMapperPackageName(nested.nestedTargetClassName) + "." + nested.nestedMapperSimpleName;
        return nested;
    }

    private void writeMapStructMapper(PairModel model) {
        try {
            String sourceParam = toPoParamName(model.sourceSimpleName);
            StringBuilder body = new StringBuilder();
            body.append("package ").append(model.mapperPackageName).append(";\n\n");
            body.append("import io.github.easytrans.core.context.TranslationContext;\n");
            body.append("import ").append(model.sourceClassName).append(";\n");
            body.append("import ").append(model.targetClassName).append(";\n");
            body.append("import org.mapstruct.Context;\n");
            body.append("import org.mapstruct.Mapper;\n");
            body.append("import org.mapstruct.Mapping;\n\n");

            if (enableSpring) {
                String usesClause = model.nestedFields.isEmpty()
                        ? ""
                        : ", uses = {" + String.join(", ", model.nestedFields.stream()
                        .map(n -> n.nestedMapperClassName + ".class").distinct().toList()) + "}";
                body.append("@Mapper(componentModel = \"spring\"").append(usesClause).append(")\n");
            } else {
                if (model.nestedFields.isEmpty()) {
                    body.append("@Mapper\n");
                } else {
                    String usesClause = "uses = {" + String.join(", ", model.nestedFields.stream()
                            .map(n -> n.nestedMapperClassName + ".class").distinct().toList()) + "}";
                    body.append("@Mapper(").append(usesClause).append(")\n");
                }
            }

            body.append("public interface ").append(model.mapperSimpleName).append(" {\n\n");
            for (TranslateFieldModel tf : model.translateFields) {
                body.append("    @Mapping(target = \"").append(tf.targetField)
                        .append("\", expression = \"java(context.getName(\\\"")
                        .append(tf.type).append("\\\", ").append(sourceParam).append(".")
                        .append(tf.sourceGetter).append("()))\")\n");
            }
            body.append("    ").append(model.targetSimpleName).append(" toVO(")
                    .append(model.sourceSimpleName).append(" ").append(sourceParam)
                    .append(", @Context TranslationContext context);\n");
            body.append("}\n");

            writeJava(model.mapperPackageName + "." + model.mapperSimpleName, body.toString());
        } catch (IOException e) {
            throw new IllegalStateException("生成 MapStruct Mapper 失败: " + model.mapperSimpleName, e);
        }
    }

    private void writeMapperBridge(PairModel model) {
        try {
            StringBuilder body = new StringBuilder();
            body.append("package ").append(model.mapperPackageName).append(";\n\n");
            body.append("import io.github.easytrans.core.context.TranslationContext;\n");
            body.append("import io.github.easytrans.core.mapstruct.BaseTranslationMapper;\n");
            body.append("import ").append(model.sourceClassName).append(";\n");
            body.append("import ").append(model.targetClassName).append(";\n");
            for (NestedFieldModel nested : model.nestedFields) {
                body.append("import ").append(nested.sourceItemClassName).append(";\n");
            }
            if (enableSpring) {
                body.append("import org.springframework.stereotype.Component;\n\n");
            } else {
                body.append("\n");
            }
            body.append("import java.util.ArrayList;\n");
            body.append("import java.util.List;\n\n");
            if (enableSpring) {
                String beanName = "easytransBridge_" + model.mapperPackageName.replace('.',
                                                                                       '_') + "_" + model.bridgeSimpleName;
                body.append("@Component(\"").append(beanName).append("\")\n");
            }
            body.append("public class ").append(model.bridgeSimpleName)
                    .append(" implements BaseTranslationMapper<")
                    .append(model.sourceSimpleName).append(", ").append(model.targetSimpleName).append("> {\n\n");

            if (enableSpring) {
                body.append("    private final ").append(model.mapperSimpleName).append(" delegate;\n\n");
                body.append("    public ").append(model.bridgeSimpleName).append("(")
                        .append(model.mapperSimpleName).append(" delegate) {\n");
                body.append("        this.delegate = delegate;\n");
                body.append("    }\n\n");
            } else {
                body.append("    private final ").append(model.mapperSimpleName).append(
                                " delegate = org.mapstruct.factory.Mappers.getMapper(")
                        .append(model.mapperSimpleName).append(".class);\n\n");
                body.append("    public ").append(model.bridgeSimpleName).append("() {\n");
                body.append("    }\n\n");
            }

            body.append("    @Override\n");
            body.append("    public void extractAllIds(List<").append(model.sourceSimpleName)
                    .append("> pos, TranslationContext context) {\n");
            body.append("        if (pos == null) {\n");
            body.append("            return;\n");
            body.append("        }\n");
            body.append("        for (").append(model.sourceSimpleName).append(" po : pos) {\n");
            body.append("            if (po == null) {\n");
            body.append("                continue;\n");
            body.append("            }\n");
            for (TranslateFieldModel tf : model.translateFields) {
                body.append("            context.collectId(\"")
                        .append(tf.type).append("\", po.").append(tf.sourceGetter).append("());\n");
            }
            for (NestedFieldModel nested : model.nestedFields) {
                body.append("            if (po.get").append(capitalize(nested.fieldName))
                        .append("() != null) {\n");
                body.append("                for (").append(nested.sourceItemSimpleName)
                        .append(" item : po.get").append(capitalize(nested.fieldName)).append("()) {\n");
                body.append("                    if (item == null) {\n");
                body.append("                        continue;\n");
                body.append("                    }\n");
                appendNestedCollect(body, nested.nestedTargetClassName, "item");
                body.append("                }\n");
                body.append("            }\n");
            }
            body.append("        }\n");
            body.append("    }\n\n");

            body.append("    @Override\n");
            body.append("    public List<").append(model.targetSimpleName).append("> toTargetList(List<")
                    .append(model.sourceSimpleName).append("> pos, TranslationContext context) {\n");
            body.append("        if (pos == null) {\n");
            body.append("            return null;\n");
            body.append("        }\n");
            body.append("        List<").append(model.targetSimpleName).append("> list = new ArrayList<>(pos.size());\n");
            body.append("        for (").append(model.sourceSimpleName).append(" po : pos) {\n");
            body.append("            list.add(delegate.toVO(po, context));\n");
            body.append("        }\n");
            body.append("        return list;\n");
            body.append("    }\n");
            body.append("}\n");

            writeJava(model.mapperPackageName + "." + model.bridgeSimpleName, body.toString());
        } catch (IOException e) {
            throw new IllegalStateException("生成 MapperBridge 失败: " + model.bridgeSimpleName, e);
        }
    }

    private void appendNestedCollect(StringBuilder body, String nestedTargetClassName, String varName) {
        TypeElement targetElement = elements.getTypeElement(nestedTargetClassName);
        for (VariableElement field : ElementFilter.fieldsIn(targetElement.getEnclosedElements())) {
            TranslateField translate = field.getAnnotation(TranslateField.class);
            if (translate != null) {
                body.append("                    context.collectId(\"")
                        .append(translate.type()).append("\", ")
                        .append(varName).append(".").append(getter(translate.source())).append("());\n");
            }
        }
        Map<String, VariableElement> sourceFields = fieldMap(elements.getTypeElement(
                resolveSourceClassNames(targetElement).get(0)));
        for (VariableElement field : ElementFilter.fieldsIn(targetElement.getEnclosedElements())) {
            NestedFieldModel deeper = analyzeNestedField(field, sourceFields);
            if (deeper != null) {
                body.append("                    if (").append(varName).append(".get")
                        .append(capitalize(deeper.fieldName)).append("() != null) {\n");
                body.append("                        for (").append(deeper.sourceItemSimpleName)
                        .append(" sub : ").append(varName).append(".get")
                        .append(capitalize(deeper.fieldName)).append("()) {\n");
                appendNestedCollect(body, deeper.nestedTargetClassName, "sub");
                body.append("                        }\n");
                body.append("                    }\n");
            }
        }
    }

    private void writeTranslationRegistry(List<PairModel> pairs) {
        try {
            String registryPkg = getRegistryPackageName(pairs);
            StringBuilder body = new StringBuilder();
            body.append("package ").append(registryPkg).append(";\n\n");
            body.append("import io.github.easytrans.core.mapstruct.BaseTranslationMapper;\n");
            body.append("import io.github.easytrans.core.registry.TranslationRegistry;\n");
            for (PairModel pair : pairs) {
                body.append("import ").append(pair.sourceClassName).append(";\n");
                body.append("import ").append(pair.mapperPackageName).append(".").append(pair.bridgeSimpleName).append(
                        ";\n");
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
            body.append("    private final Map<Class<?>, BaseTranslationMapper<?, ?>> bySourceClass;\n\n");

            if (enableSpring) {
                body.append("    public GeneratedTranslationRegistry(");
                body.append(String.join(", ", pairs.stream()
                        .map(p -> p.bridgeSimpleName + " " + toVarName(p.bridgeSimpleName)).toList()));
                body.append(") {\n");
                body.append("        Map<Class<?>, BaseTranslationMapper<?, ?>> map = new HashMap<>();\n");
                for (PairModel pair : pairs) {
                    String var = toVarName(pair.bridgeSimpleName);
                    body.append("        map.put(").append(simpleName(pair.sourceClassName))
                            .append(".class, ").append(var).append(");\n");
                }
                body.append("        this.bySourceClass = Map.copyOf(map);\n");
                body.append("    }\n\n");
            } else {
                body.append("    public GeneratedTranslationRegistry() {\n");
                body.append("        Map<Class<?>, BaseTranslationMapper<?, ?>> map = new HashMap<>();\n");
                for (PairModel pair : pairs) {
                    body.append("        map.put(").append(simpleName(pair.sourceClassName))
                            .append(".class, new ").append(pair.mapperPackageName).append(".").append(pair.bridgeSimpleName).append(
                                    "());\n");
                }
                body.append("        this.bySourceClass = Map.copyOf(map);\n");
                body.append("    }\n\n");
            }
            body.append("    @Override\n");
            body.append("    public BaseTranslationMapper<?, ?> findMapperBySourceClass(Class<?> sourceClass) {\n");
            body.append("        return bySourceClass.get(sourceClass);\n");
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

    private List<String> resolveSourceClassNames(Element element) {
        TranslateFrom translateFrom = element.getAnnotation(TranslateFrom.class);
        if (translateFrom == null) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        try {
            Class<?>[] classes = translateFrom.value();
            for (Class<?> clazz : classes) {
                list.add(clazz.getCanonicalName());
            }
        } catch (MirroredTypesException ex) {
            for (TypeMirror mirror : ex.getTypeMirrors()) {
                list.add(mirror.toString());
            }
        }
        return list;
    }

    private Map<String, VariableElement> fieldMap(TypeElement typeElement) {
        Map<String, VariableElement> map = new LinkedHashMap<>();
        for (VariableElement field : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
            map.put(field.getSimpleName().toString(), field);
        }
        return map;
    }

    private String toMapperSimpleName(String voSimpleName) {
        if (voSimpleName.endsWith("VO")) {
            return voSimpleName.substring(0, voSimpleName.length() - 2) + "AutoMapper";
        }
        return voSimpleName + "AutoMapper";
    }

    private String toPoParamName(String poSimpleName) {
        return Character.toLowerCase(poSimpleName.charAt(0)) + poSimpleName.substring(1);
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

    private static final class PairModel {
        String targetClassName;
        String sourceClassName;
        String targetSimpleName;
        String sourceSimpleName;
        String mapperSimpleName;
        String bridgeSimpleName;
        String mapperPackageName;
        final List<TranslateFieldModel> translateFields = new ArrayList<>();
        final List<NestedFieldModel> nestedFields = new ArrayList<>();
    }

    private static final class TranslateFieldModel {
        String targetField;
        String sourceField;
        String type;
        String sourceGetter;
    }

    private static final class NestedFieldModel {
        String fieldName;
        String sourceItemClassName;
        String sourceItemSimpleName;
        String nestedTargetClassName;
        String nestedMapperSimpleName;
        String nestedMapperClassName;
    }
}
