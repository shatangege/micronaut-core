package org.particleframework.core.convert;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sun.javafx.scene.control.behavior.OptionalBoolean;
import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.convert.format.ReadableBytesTypeConverter;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.core.type.Argument;
import org.particleframework.core.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The default conversion service. Handles basic type conversion operations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultConversionService implements ConversionService<DefaultConversionService> {

    private final Map<ConvertiblePair, TypeConverter> typeConverters = new ConcurrentHashMap<>();
    private final Cache<ConvertiblePair, TypeConverter> converterCache = Caffeine.newBuilder()
            .maximumSize(60)
            .softValues()
            .build();

    public DefaultConversionService() {
        registerDefaultConverters();
    }


    @Override
    public <T> Optional<T> convert(Object object, Class<T> targetType, ConversionContext context) {
        if (object == null) {
            return Optional.empty();
        }
        if(targetType == Object.class) {
            return Optional.of((T) object);
        }
        Class<?> sourceType = object.getClass();
        targetType = ReflectionUtils.getWrapperType(targetType);

        if (targetType.isInstance(object) && !Iterable.class.isInstance(object) && !Map.class.isInstance(object)) {
            return Optional.of((T) object);
        }

        Annotation formattingAnnotation = AnnotationUtil.findAnnotationWithStereoType(Format.class, context.getAnnotations());

        Class<? extends Annotation> formattingType = formattingAnnotation != null ? formattingAnnotation.annotationType() : null;
        ConvertiblePair pair = new ConvertiblePair(sourceType, targetType, formattingType);
        TypeConverter typeConverter = converterCache.getIfPresent(pair);
        if (typeConverter == null) {
            typeConverter = findTypeConverter(sourceType, targetType, formattingType);
            if (typeConverter == null) {
                return Optional.empty();
            } else {
                converterCache.put(pair, typeConverter);
            }
        }
        return typeConverter.convert(object, targetType, context);
    }

    @Override
    public <S, T> DefaultConversionService addConverter(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter) {
        ConvertiblePair pair = newPair(sourceType, targetType, typeConverter);
        typeConverters.put(pair, typeConverter);
        converterCache.put(pair, typeConverter);
        return this;
    }

    @Override
    public <S, T> DefaultConversionService addConverter(Class<S> sourceType, Class<T> targetType, Function<S, T> function) {
        ConvertiblePair pair = new ConvertiblePair(sourceType, targetType);
        TypeConverter<S, T> typeConverter = TypeConverter.of(sourceType, targetType, function);
        typeConverters.put(pair, typeConverter);
        converterCache.put(pair, typeConverter);
        return this;
    }

    protected void registerDefaultConverters() {
        // CharSequence -> Long for bytes
        addConverter(CharSequence.class, Number.class, new ReadableBytesTypeConverter());

        // CharSequence -> ZonedDateTime
        addConverter(
                CharSequence.class,
                ZonedDateTime.class,
                (object, targetType, context) -> {
                    try {
                        DateTimeFormatter formatter = resolveFormatter(context);
                        ZonedDateTime result = ZonedDateTime.parse(object, formatter);
                        return Optional.of(result);
                    } catch (DateTimeParseException e) {
                        return Optional.empty();
                    }
                }
        );

        // CharSequence -> Date
        addConverter(
                CharSequence.class,
                Date.class,
                (object, targetType, context) -> {
                    try {
                        SimpleDateFormat format = resolveFormat(context);
                        return Optional.of(format.parse(object.toString()));
                    } catch (ParseException e) {
                        return Optional.empty();
                    }
                }
        );

        // CharSequence -> LocalDataTime
        addConverter(
                CharSequence.class,
                LocalDateTime.class,
                (object, targetType, context) -> {
                    try {
                        DateTimeFormatter formatter = resolveFormatter(context);
                        LocalDateTime result = LocalDateTime.parse(object, formatter);
                        return Optional.of(result);
                    } catch (DateTimeParseException e) {
                        return Optional.empty();
                    }
                }
        );

        // CharSequence -> LocalDate
        addConverter(
                CharSequence.class,
                LocalDate.class,
                (object, targetType, context) -> {
                    try {
                        DateTimeFormatter formatter = resolveFormatter(context);
                        LocalDate result = LocalDate.parse(object, formatter);
                        return Optional.of(result);
                    } catch (DateTimeParseException e) {
                        return Optional.empty();
                    }
                }
        );

        // String -> Integer
        addConverter(CharSequence.class, Integer.class, (CharSequence object, Class<Integer> targetType, ConversionContext context) -> {
            try {
                Integer converted = Integer.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Number
        addConverter(CharSequence.class, Number.class, (CharSequence object, Class<Number> targetType, ConversionContext context) -> {
            try {
                Integer converted = Integer.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Long
        addConverter(CharSequence.class, Long.class, (CharSequence object, Class<Long> targetType, ConversionContext context) -> {
            try {
                Long converted = Long.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Short
        addConverter(CharSequence.class, Short.class, (CharSequence object, Class<Short> targetType, ConversionContext context) -> {
            try {
                Short converted = Short.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> BigDecimal
        addConverter(CharSequence.class, BigDecimal.class, (CharSequence object, Class<BigDecimal> targetType, ConversionContext context) -> {
            try {
                BigDecimal converted = new BigDecimal(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Boolean
        addConverter(CharSequence.class, Boolean.class, (CharSequence object, Class<Boolean> targetType, ConversionContext context) -> {
            String booleanString = object.toString().toLowerCase(Locale.ENGLISH);
            switch (booleanString) {
                case "yes":
                case "y":
                case "on":
                case "true":
                    return Optional.of(Boolean.TRUE);
                default:
                    return Optional.of(Boolean.FALSE);
            }
        });

        // String -> URL
        addConverter(CharSequence.class, URL.class, (CharSequence object, Class<URL> targetType, ConversionContext context) -> {
            try {
                return Optional.of(new URL(object.toString()));
            } catch (MalformedURLException e) {
                return Optional.empty();
            }
        });

        // String -> URI
        addConverter(CharSequence.class, URI.class, (CharSequence object, Class<URI> targetType, ConversionContext context) -> {
            try {
                return Optional.of(new URI(object.toString()));
            } catch (URISyntaxException e) {
                return Optional.empty();
            }
        });

        // String -> Locale
        addConverter(CharSequence.class, Locale.class, (CharSequence object, Class<Locale> targetType, ConversionContext context) -> {
            try {
                return Optional.of(Locale.forLanguageTag(object.toString().replace('_', '-')));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });

        // String -> UUID
        addConverter(CharSequence.class, UUID.class, (CharSequence object, Class<UUID> targetType, ConversionContext context) -> {
            try {
                return Optional.of(UUID.fromString(object.toString()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });

        // String -> Currency
        addConverter(CharSequence.class, Currency.class, (CharSequence object, Class<Currency> targetType, ConversionContext context) -> {
            try {
                return Optional.of(Currency.getInstance(object.toString()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });


        // String -> TimeZone
        addConverter(CharSequence.class, TimeZone.class, (CharSequence object, Class<TimeZone> targetType, ConversionContext context) -> Optional.of(TimeZone.getTimeZone(object.toString())));

        // String -> Charset
        addConverter(CharSequence.class, Charset.class, (CharSequence object, Class<Charset> targetType, ConversionContext context) -> {
            try {
                return Optional.of(Charset.forName(object.toString()));
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                return Optional.empty();
            }
        });

        // String -> Character
        addConverter(CharSequence.class, Character.class, (CharSequence object, Class<Character> targetType, ConversionContext context) -> {
            String str = object.toString();
            if (str.length() == 1) {
                return Optional.of(str.charAt(0));
            } else {
                return Optional.empty();
            }
        });

        // String -> Array
        addConverter(CharSequence.class, Object[].class, (CharSequence object, Class<Object[]> targetType, ConversionContext context) -> {
            String str = object.toString();
            String[] strings = str.split(",");
            Class<?> componentType = ReflectionUtils.getWrapperType(targetType.getComponentType());
            Object newArray = Array.newInstance(componentType, strings.length);
            for (int i = 0; i < strings.length; i++) {
                String string = strings[i];
                Optional<?> converted = convert(string, componentType);
                if (converted.isPresent()) {
                    Array.set(newArray, i, converted.get());
                }
            }
            return Optional.of((Object[]) newArray);
        });

        // String -> Enum
        addConverter(CharSequence.class, Enum.class, (CharSequence object, Class<Enum> targetType, ConversionContext context) -> {
            String stringValue = object.toString();
            try {
                Enum val = Enum.valueOf(targetType, stringValue);
                return Optional.of(val);
            } catch (IllegalArgumentException e) {
                try {
                    Enum val = Enum.valueOf(targetType, NameUtils.underscoreSeparate(stringValue).toUpperCase(Locale.ENGLISH));
                    return Optional.of(val);
                } catch (Exception e1) {
                    return Optional.empty();
                }
            }
        });

        // Object -> String
        addConverter(Object.class, String.class, (Object object, Class<String> targetType, ConversionContext context) -> Optional.of(object.toString()));

        // Number -> Number
        addConverter(Number.class, Number.class, (Number object, Class<Number> targetType, ConversionContext context) -> {
            Class targetNumberType = ReflectionUtils.getWrapperType(targetType);
            if (targetNumberType.isInstance(object)) {
                return Optional.of(object);
            } else if (targetNumberType == Integer.class) {
                return Optional.of(object.intValue());
            } else if (targetNumberType == Long.class) {
                return Optional.of(object.longValue());
            } else if (targetNumberType == Short.class) {
                return Optional.of(object.shortValue());
            } else if (targetNumberType == Byte.class) {
                return Optional.of(object.byteValue());
            } else if (targetNumberType == Float.class) {
                return Optional.of(object.floatValue());
            } else if (targetNumberType == Double.class) {
                return Optional.of(object.doubleValue());
            } else if (targetNumberType == BigInteger.class) {
                if (object instanceof BigDecimal) {
                    return Optional.of(((BigDecimal) object).toBigInteger());
                } else {
                    return Optional.of(BigInteger.valueOf(object.longValue()));
                }
            } else if (targetNumberType == BigDecimal.class) {
                return Optional.of(new BigDecimal(object.toString()));
            }
            return Optional.empty();
        });

        // String -> List/Iterable
        addConverter(CharSequence.class, Iterable.class, (CharSequence object, Class<Iterable> targetType, ConversionContext context) -> {
            Optional<Argument<?>> typeVariable = context.getFirstTypeVariable();
            Class targetComponentType = typeVariable.map(arg -> (Class) arg.getType()).orElse(Object.class);
            ConversionContext newContext = typeVariable.map(ConversionContext::of).orElse(context);

            targetComponentType = ReflectionUtils.getWrapperType(targetComponentType);
            String[] strings = object.toString().split(",");
            List list = new ArrayList();
            for (String string : strings) {
                Optional converted = convert(string, targetComponentType, newContext);
                if (converted.isPresent()) {
                    list.add(converted.get());
                }
            }
            return Optional.of(list);
        });

        // Optional handling
        addConverter(Object.class, Optional.class, (object, targetType, context) -> {
            Optional<Argument<?>> typeVariable = context.getFirstTypeVariable();
            Class targetComponentType = typeVariable.map(arg -> (Class) arg.getType()).orElse(Object.class);
            ConversionContext newContext = typeVariable.map(ConversionContext::of).orElse(context);

            targetComponentType = ReflectionUtils.getWrapperType(targetComponentType);
            Optional converted = convert(object, targetComponentType, newContext);
            if (converted.isPresent()) {
                return Optional.of(converted);
            } else {
                return Optional.of(Optional.empty());
            }
        });

        addConverter(Object.class, OptionalInt.class, (object, targetType, context) -> {
            Optional<Integer> converted = convert(object, Integer.class, context);
            return converted.map(integer -> Optional.of(OptionalInt.of(integer))).orElseGet(() -> Optional.of(OptionalInt.empty()));
        });

        addConverter(Object.class, OptionalLong.class, (object, targetType, context) -> {
            Optional<Long> converted = convert(object, Long.class, context);
            return converted.map(longValue -> Optional.of(OptionalLong.of(longValue))).orElseGet(() -> Optional.of(OptionalLong.empty()));
        });



        // Iterable -> Iterable (inner type conversion)
        addConverter(Iterable.class, Iterable.class, (object, targetType, context) -> {
            Optional<Argument<?>> typeVariable = context.getFirstTypeVariable();
            Class targetComponentType = typeVariable.map(arg -> (Class) arg.getType()).orElse(Object.class);
            ConversionContext newContext = typeVariable.map(ConversionContext::of).orElse(context);
            if (targetType.isInstance(object)) {
                if(targetComponentType == Object.class) {
                    return Optional.of(object);
                }
                List list = new ArrayList();
                for (Object o : object) {
                    Optional converted = convert(o, targetComponentType, newContext);
                    if (converted.isPresent()) {
                        list.add(converted.get());
                    }
                }
                return CollectionUtils.convertCollection((Class) targetType, list);
            } else {
                targetComponentType = Object.class;
                List list = new ArrayList();
                for (Object o : object) {
                    list.add(convert(o, targetComponentType, newContext));
                }
                return CollectionUtils.convertCollection((Class) targetType, list);
            }
        });

        // Map -> Map (inner type conversion)
        addConverter(Map.class, Map.class, (object, targetType, context) -> {
            TypeVariable<Class<Map>>[] typeParameters = targetType.getTypeParameters();
            Class keyType = Object.class;
            Class valueType = Object.class;
            boolean isProperties = false;
            ConversionContext keyContext = context;
            ConversionContext valContext = context;
            if (targetType.equals(Properties.class)) {
                keyType = String.class;
                valueType = String.class;
                isProperties = true;
            } else if (typeParameters.length == 2) {

                Optional<Argument<?>> keyVar = context.getTypeVariable(typeParameters[0].getName());

                keyType = keyVar
                        .map(arg -> (Class) arg.getType())
                        .orElse(String.class);
                keyContext = keyVar.map(ConversionContext::of).orElse(context);

                Optional<Argument<?>> valVar = context.getTypeVariable(typeParameters[1].getName());
                valueType = valVar
                        .map(arg -> (Class) arg.getType())
                        .orElse(Object.class);
                valContext = valVar.map(ConversionContext::of).orElse(context);
            }
            Map newMap = isProperties ? new Properties() : new LinkedHashMap();

            for (Object o : object.entrySet()) {
                Map.Entry entry = (Map.Entry) o;
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (!keyType.isInstance(key)) {
                    Optional convertedKey = convert(key, keyType, keyContext);
                    if (convertedKey.isPresent()) {
                        key = convertedKey.get();
                    } else {
                        continue;
                    }
                }
                if (!valueType.isInstance(value)) {
                    Optional converted = convert(value, valueType, valContext);
                    if (converted.isPresent()) {
                        value = converted.get();
                    } else {
                        continue;
                    }
                }
                newMap.put(key, value);
            }
            return Optional.of(newMap);
        });

    }

    private DateTimeFormatter resolveFormatter(ConversionContext context) {
        Format ann = context.getAnnotation(Format.class);
        Optional<String> format = ann != null ? Optional.of(ann.value()) : Optional.empty();
        return format
                .map((pattern) -> DateTimeFormatter.ofPattern(pattern, context.getLocale()))
                .orElse(DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    private SimpleDateFormat resolveFormat(ConversionContext context) {
        Format ann = context.getAnnotation(Format.class);
        Optional<String> format = ann != null ? Optional.of(ann.value()) : Optional.empty();
        return format
                .map((pattern) -> new SimpleDateFormat(pattern, context.getLocale()))
                .orElse(new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", context.getLocale()));
    }

    private <S, T> ConvertiblePair newPair(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter) {
        ConvertiblePair pair;
        if (typeConverter instanceof FormattingTypeConverter) {
            pair = new ConvertiblePair(sourceType, targetType, ((FormattingTypeConverter) typeConverter).annotationType());
        } else {
            pair = new ConvertiblePair(sourceType, targetType);
        }
        return pair;
    }


    protected <T> TypeConverter findTypeConverter(Class<?> sourceType, Class<T> targetType, Class<? extends Annotation> formattingAnnotation) {
        TypeConverter typeConverter = null;
        List<Class> sourceHierarchy = resolveHierarchy(sourceType);
        List<Class> targetHierarchy = resolveHierarchy(targetType);
        boolean hasFormatting = formattingAnnotation != null;
        for (Class sourceSuperType : sourceHierarchy) {
            for (Class targetSuperType : targetHierarchy) {
                ConvertiblePair pair = new ConvertiblePair(sourceSuperType, targetSuperType, formattingAnnotation);
                typeConverter = typeConverters.get(pair);
                if (typeConverter != null) {
                    converterCache.put(pair, typeConverter);
                    return typeConverter;
                }
            }
        }
        if (hasFormatting) {
            for (Class sourceSuperType : sourceHierarchy) {
                for (Class targetSuperType : targetHierarchy) {
                    ConvertiblePair pair = new ConvertiblePair(sourceSuperType, targetSuperType);
                    typeConverter = typeConverters.get(pair);
                    if (typeConverter != null) {
                        converterCache.put(pair, typeConverter);
                        return typeConverter;
                    }
                }
            }
        }
        return typeConverter;
    }

    private void populateHierarchyInterfaces(Class<?> superclass, List<Class> hierarchy) {
        if (!hierarchy.contains(superclass)) {
            hierarchy.add(superclass);
        }
        for (Class<?> aClass : superclass.getInterfaces()) {
            if (!hierarchy.contains(aClass)) {
                hierarchy.add(aClass);
            }
            populateHierarchyInterfaces(aClass, hierarchy);
        }
    }

    private List<Class> resolveHierarchy(Class<?> type) {
        Class<?> superclass = type.getSuperclass();
        List<Class> hierarchy = new ArrayList<>();
        if (superclass != null) {
            populateHierarchyInterfaces(type, hierarchy);

            while (superclass != Object.class) {
                populateHierarchyInterfaces(superclass, hierarchy);
                superclass = superclass.getSuperclass();
            }
        } else if (type.isInterface()) {
            populateHierarchyInterfaces(type, hierarchy);
        }

        if (type.isArray()) {
            hierarchy.add(Object[].class);
        } else {
            hierarchy.add(Object.class);
        }

        return hierarchy;
    }

    private class ConvertiblePair {
        final Class source;
        final Class target;
        final Class<? extends Annotation> formattingAnnotation;

        ConvertiblePair(Class source, Class target) {
            this(source, target, null);
        }

        public ConvertiblePair(Class source, Class target, Class<? extends Annotation> formattingAnnotation) {
            this.source = source;
            this.target = target;
            this.formattingAnnotation = formattingAnnotation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConvertiblePair pair = (ConvertiblePair) o;

            if (!source.equals(pair.source)) return false;
            if (!target.equals(pair.target)) return false;
            return formattingAnnotation != null ? formattingAnnotation.equals(pair.formattingAnnotation) : pair.formattingAnnotation == null;
        }

        @Override
        public int hashCode() {
            int result = source.hashCode();
            result = 31 * result + target.hashCode();
            result = 31 * result + (formattingAnnotation != null ? formattingAnnotation.hashCode() : 0);
            return result;
        }
    }
}
