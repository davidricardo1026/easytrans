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

    private TypeMirror collectionTypeMirror;
    private TypeMirror mapTypeMirror;

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

        TypeElement colEl = elements.getTypeElement("java.util.Collection");
        this.collectionTypeMirror = colEl != null ? processingEnv.getTypeUtils().erasure(colEl.asType()) : null;
        TypeElement mapEl = elements.getTypeElement("java.util.Map");
        this.mapTypeMirror = mapEl != null ? processingEnv.getTypeUtils().erasure(mapEl.asType()) : null;
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

                String fieldName = targetField.getSimpleName().toString();
                if (isTranslatable(targetField.asType())) {
                    if (!sourceFields.containsKey(fieldName)) {
                        throw new IllegalStateException("Source 缺少嵌套字段: " + fieldName);
                    }
                    NestedFieldModel nested = new NestedFieldModel();
                    nested.fieldName = fieldName;
                    nested.poFieldName = fieldName;
                    nested.voFieldType = targetField.asType();
                    nested.poFieldType = sourceFields.get(fieldName).asType();
                    model.nestedFields.add(nested);
                }
            }
            models.add(model);
        }
        return models;
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
            body.append("import org.mapstruct.Mapping;\n");
            body.append("import java.util.List;\n");
            body.append("import java.util.Set;\n");
            body.append("import java.util.Map;\n\n");

            Set<String> nestedMapperClassNames = new LinkedHashSet<>();
            for (NestedFieldModel nested : model.nestedFields) {
                Set<TypeElement> nestedTargets = new LinkedHashSet<>();
                collectNestedTranslatables(nested.voFieldType, nestedTargets);
                for (TypeElement targetNested : nestedTargets) {
                    List<String> sources = resolveSourceClassNames(targetNested);
                    for (String source : sources) {
                        String mapperSimpleName = simpleName(source) + "To" + targetNested.getSimpleName().toString() + "AutoMapper";
                        String mapperPkg = getMapperPackageName(targetNested.getQualifiedName().toString());
                        nestedMapperClassNames.add(mapperPkg + "." + mapperSimpleName + ".class");
                    }
                }
            }

            String usesClause = "";
            if (!nestedMapperClassNames.isEmpty()) {
                usesClause = ", uses = {" + String.join(", ", nestedMapperClassNames) + "}";
            }

            if (enableSpring) {
                body.append("@Mapper(componentModel = \"spring\"").append(usesClause).append(")\n");
            } else {
                if (nestedMapperClassNames.isEmpty()) {
                    body.append("@Mapper\n");
                } else {
                    body.append("@Mapper(").append(usesClause.substring(2)).append(")\n");
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
                    .append(", @Context TranslationContext context);\n\n");

            body.append("    List<").append(model.targetSimpleName).append("> toVOList(List<")
                    .append(model.sourceSimpleName).append("> pos, @Context TranslationContext context);\n\n");

            body.append("    Set<").append(model.targetSimpleName).append("> toVOSet(Set<")
                    .append(model.sourceSimpleName).append("> pos, @Context TranslationContext context);\n\n");

            body.append("    default <K> Map<K, ").append(model.targetSimpleName).append("> toVOMap(Map<K, ")
                    .append(model.sourceSimpleName).append("> pos, @Context TranslationContext context) {\n")
                    .append("        if (pos == null) return null;\n")
                    .append("        Map<K, ").append(model.targetSimpleName).append(
                            "> map = new java.util.LinkedHashMap<>();\n")
                    .append("        for (Map.Entry<K, ").append(model.sourceSimpleName).append(
                            "> entry : pos.entrySet()) {\n")
                    .append("            map.put(entry.getKey(), toVO(entry.getValue(), context));\n")
                    .append("        }\n")
                    .append("        return map;\n")
                    .append("    }\n\n");

            body.append("    default <K> Map<").append(model.targetSimpleName).append(", K> toVOMapKey(Map<")
                    .append(model.sourceSimpleName).append(", K> pos, @Context TranslationContext context) {\n")
                    .append("        if (pos == null) return null;\n")
                    .append("        Map<").append(model.targetSimpleName).append(
                            ", K> map = new java.util.LinkedHashMap<>();\n")
                    .append("        for (Map.Entry<").append(model.sourceSimpleName).append(
                            ", K> entry : pos.entrySet()) {\n")
                    .append("            map.put(toVO(entry.getKey(), context), entry.getValue());\n")
                    .append("        }\n")
                    .append("        return map;\n")
                    .append("    }\n\n");

            Set<TypePair> signatures = new LinkedHashSet<>();
            for (NestedFieldModel nested : model.nestedFields) {
                collectIntermediateSignatures(nested.voFieldType, nested.poFieldType, signatures);
            }

            int sigIdx = 1;
            for (TypePair pair : signatures) {
                body.append("    ").append(pair.voStr).append(" mapNested").append(sigIdx++)
                        .append("(").append(pair.poStr).append(" value, @Context TranslationContext context);\n\n");
            }

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
            body.append("        extractIds(pos, context);\n");
            body.append("    }\n\n");

            body.append("    public static void extractIds(List<").append(model.sourceSimpleName)
                    .append("> pos, TranslationContext context) {\n");
            body.append("        if (pos == null) {\n");
            body.append("            return;\n");
            body.append("        }\n");
            body.append("        for (").append(model.sourceSimpleName).append(" po : pos) {\n");
            body.append("            extractIds(po, context);\n");
            body.append("        }\n");
            body.append("    }\n\n");

            body.append("    public static void extractIds(").append(model.sourceSimpleName)
                    .append(" po, TranslationContext context) {\n");
            body.append("        if (po == null) {\n");
            body.append("            return;\n");
            body.append("        }\n");
            for (TranslateFieldModel tf : model.translateFields) {
                body.append("        context.collectId(\"")
                        .append(tf.type).append("\", po.").append(tf.sourceGetter).append("());\n");
            }
            for (NestedFieldModel nested : model.nestedFields) {
                String poGetter = "po." + getter(nested.poFieldName) + "()";
                String extractionCode = generateRecursiveExtract(nested.poFieldType,
                                                                 nested.voFieldType,
                                                                 poGetter,
                                                                 1,
                                                                 "        ");
                body.append(extractionCode);
            }
            body.append("    }\n\n");

            body.append("    @Override\n");
            body.append("    public List<").append(model.targetSimpleName).append("> toTargetList(List<")
                    .append(model.sourceSimpleName).append("> pos, TranslationContext context) {\n");
            body.append("        return delegate.toVOList(pos, context);\n");
            body.append("    }\n");
            body.append("}\n");

            writeJava(model.mapperPackageName + "." + model.bridgeSimpleName, body.toString());
        } catch (IOException e) {
            throw new IllegalStateException("生成 MapperBridge 失败: " + model.bridgeSimpleName, e);
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

    private boolean isTranslatable(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        if (element.getAnnotation(TranslateFrom.class) != null) {
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

    private boolean isComplexNested(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        if (element.getAnnotation(TranslateFrom.class) != null) {
            return false;
        }
        if (isCollection(type)) {
            TypeMirror itemType = declared.getTypeArguments().get(0);
            if (itemType.getKind() == TypeKind.DECLARED) {
                DeclaredType declaredItem = (DeclaredType) itemType;
                if (declaredItem.asElement().getAnnotation(TranslateFrom.class) != null) {
                    return false;
                }
            }
            return isTranslatable(itemType);
        }
        if (isMap(type)) {
            TypeMirror valueType = declared.getTypeArguments().get(1);
            if (valueType.getKind() == TypeKind.DECLARED) {
                DeclaredType declaredVal = (DeclaredType) valueType;
                if (declaredVal.asElement().getAnnotation(TranslateFrom.class) != null) {
                    return false;
                }
            }
            return isTranslatable(valueType);
        }
        return false;
    }

    private void collectNestedTranslatables(TypeMirror type, Set<TypeElement> result) {
        if (type.getKind() != TypeKind.DECLARED) {
            return;
        }
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        if (element.getAnnotation(TranslateFrom.class) != null) {
            result.add(element);
            return;
        }
        for (TypeMirror arg : declared.getTypeArguments()) {
            collectNestedTranslatables(arg, result);
        }
    }

    private int getNestingDepth(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return 0;
        }
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        if (element.getAnnotation(TranslateFrom.class) != null) {
            return 0;
        }
        if (isCollection(type)) {
            if (!declared.getTypeArguments().isEmpty()) {
                return 1 + getNestingDepth(declared.getTypeArguments().get(0));
            }
        }
        if (isMap(type)) {
            if (declared.getTypeArguments().size() >= 2) {
                return 1 + Math.max(
                        getNestingDepth(declared.getTypeArguments().get(0)),
                        getNestingDepth(declared.getTypeArguments().get(1))
                );
            }
        }
        return 0;
    }

    private void collectIntermediateSignatures(TypeMirror voType, TypeMirror poType, Set<TypePair> signatures) {
        if (voType.getKind() != TypeKind.DECLARED || poType.getKind() != TypeKind.DECLARED) {
            return;
        }
        DeclaredType declaredVo = (DeclaredType) voType;
        DeclaredType declaredPo = (DeclaredType) poType;
        TypeElement elementVo = (TypeElement) declaredVo.asElement();

        if (elementVo.getAnnotation(TranslateFrom.class) != null) {
            return;
        }

        if (getNestingDepth(voType) > 1) {
            signatures.add(new TypePair(voType, poType));
        }

        if (isCollection(voType)) {
            if (!declaredVo.getTypeArguments().isEmpty() && !declaredPo.getTypeArguments().isEmpty()) {
                TypeMirror voArg = declaredVo.getTypeArguments().get(0);
                TypeMirror poArg = declaredPo.getTypeArguments().get(0);
                if (isTranslatable(voArg)) {
                    collectIntermediateSignatures(voArg, poArg, signatures);
                }
            }
        } else if (isMap(voType)) {
            if (declaredVo.getTypeArguments().size() >= 2 && declaredPo.getTypeArguments().size() >= 2) {
                TypeMirror voKey = declaredVo.getTypeArguments().get(0);
                TypeMirror poKey = declaredPo.getTypeArguments().get(0);
                TypeMirror voVal = declaredVo.getTypeArguments().get(1);
                TypeMirror poVal = declaredPo.getTypeArguments().get(1);
                boolean transKey = isTranslatable(voKey);
                boolean transVal = isTranslatable(voVal);
                if (transKey) collectIntermediateSignatures(voKey, poKey, signatures);
                if (transVal) collectIntermediateSignatures(voVal, poVal, signatures);
            }
        }
    }

    private String generateRecursiveExtract(TypeMirror poType,
                                            TypeMirror voType,
                                            String expr,
                                            int depth,
                                            String indent) {
        if (voType.getKind() != TypeKind.DECLARED) {
            return "";
        }
        DeclaredType declaredVo = (DeclaredType) voType;
        DeclaredType declaredPo = (DeclaredType) poType;
        TypeElement elementVo = (TypeElement) declaredVo.asElement();

        if (elementVo.getAnnotation(TranslateFrom.class) != null) {
            String bridgeClassName = getMapperPackageName(elementVo.getQualifiedName().toString()) + "." +
                    simpleName(declaredPo.asElement().toString()) + "To" + elementVo.getSimpleName().toString() + "AutoMapperBridge";
            return indent + "if (" + expr + " != null) {\n" +
                    indent + "    " + bridgeClassName + ".extractIds(" + expr + ", context);\n" +
                    indent + "}\n";
        }

        if (isCollection(voType)) {
            String varName = "item" + depth;
            String poItemType = declaredPo.getTypeArguments().get(0).toString();
            StringBuilder sb = new StringBuilder();
            sb.append(indent).append("if (").append(expr).append(" != null) {\n");
            sb.append(indent).append("    for (").append(poItemType).append(" ").append(varName).append(" : ").append(
                    expr).append(") {\n");
            sb.append(generateRecursiveExtract(declaredPo.getTypeArguments().get(0),
                                                      declaredVo.getTypeArguments().get(0),
                                                      varName,
                                               depth + 1,
                                               indent + "        "));
            sb.append(indent).append("    }\n");
            sb.append(indent).append("}\n");
            return sb.toString();
        }

        if (isMap(voType)) {
            String entryVar = "entry" + depth;
            String keyTypePo = declaredPo.getTypeArguments().get(0).toString();
            String valueTypePo = declaredPo.getTypeArguments().get(1).toString();

            StringBuilder sb = new StringBuilder();
            sb.append(indent).append("if (").append(expr).append(" != null) {\n");
            sb.append(indent).append("    for (java.util.Map.Entry<").append(keyTypePo).append(", ").append(valueTypePo).append(
                    "> ").append(entryVar).append(" : ").append(expr).append(".entrySet()) {\n");
            sb.append(indent).append("        if (").append(entryVar).append(" != null) {\n");

            if (isTranslatable(declaredVo.getTypeArguments().get(0))) {
                sb.append(generateRecursiveExtract(declaredPo.getTypeArguments().get(0),
                                                          declaredVo.getTypeArguments().get(0),
                                                          entryVar + ".getKey()",
                                                   depth + 1,
                                                   indent + "            "));
            }

            if (isTranslatable(declaredVo.getTypeArguments().get(1))) {
                sb.append(generateRecursiveExtract(declaredPo.getTypeArguments().get(1),
                                                          declaredVo.getTypeArguments().get(1),
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

    private String indent(String code, String prefix) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        String[] lines = code.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (!line.isEmpty()) {
                sb.append(prefix).append(line).append("\n");
            } else {
                sb.append("\n");
            }
        }
        return sb.toString();
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
        String poFieldName;
        TypeMirror voFieldType;
        TypeMirror poFieldType;
    }

    private static final class TypePair {
        TypeMirror voType;
        TypeMirror poType;
        String voStr;
        String poStr;

        public TypePair(TypeMirror voType, TypeMirror poType) {
            this.voType = voType;
            this.poType = poType;
            this.voStr = voType.toString();
            this.poStr = poType.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TypePair)) return false;
            TypePair typePair = (TypePair) o;
            return voStr.equals(typePair.voStr) && poStr.equals(typePair.poStr);
        }

        @Override
        public int hashCode() {
            return Objects.hash(voStr, poStr);
        }
    }
}
