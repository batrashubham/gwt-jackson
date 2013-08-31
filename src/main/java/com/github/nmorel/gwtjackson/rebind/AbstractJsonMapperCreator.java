package com.github.nmorel.gwtjackson.rebind;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JEnumType;
import com.google.gwt.core.ext.typeinfo.JParameterizedType;
import com.google.gwt.core.ext.typeinfo.JPrimitiveType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.user.rebind.AbstractSourceCreator;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;

/** @author Nicolas Morel */
public abstract class AbstractJsonMapperCreator extends AbstractSourceCreator
{
    protected static final List<String> BASE_TYPES = Arrays
        .asList( "java.math.BigDecimal", "java.math.BigInteger", "java.lang.Boolean", "java.lang.Byte", "java.lang.Character",
            "java.util.Date", "java.lang.Double", "java.lang.Float", "java.lang.Integer", "java.lang.Long", "java.lang.Short",
            "java.sql.Date", "java.sql.Time", "java.sql.Timestamp", "java.lang.String" );
    protected static final String JSON_MAPPER_CLASS = "com.github.nmorel.gwtjackson.client.JsonMapper";
    protected static final String ABSTRACT_JSON_MAPPER_CLASS = "com.github.nmorel.gwtjackson.client.AbstractJsonMapper";
    protected static final String ABSTRACT_BEAN_JSON_MAPPER_CLASS = "com.github.nmorel.gwtjackson.client.mapper.AbstractBeanJsonMapper";
    protected static final String DECODER_PROPERTY_BEAN_CLASS = "com.github.nmorel.gwtjackson.client.mapper.AbstractBeanJsonMapper.DecoderProperty";
    protected static final String ENCODER_PROPERTY_BEAN_CLASS = "com.github.nmorel.gwtjackson.client.mapper.AbstractBeanJsonMapper.EncoderProperty";
    protected static final String JSON_READER_CLASS = "com.github.nmorel.gwtjackson.client.stream.JsonReader";
    protected static final String JSON_WRITER_CLASS = "com.github.nmorel.gwtjackson.client.stream.JsonWriter";
    protected static final String JSON_MAPPING_CONTEXT_CLASS = "com.github.nmorel.gwtjackson.client.JsonMappingContext";
    protected static final String JSON_DECODING_CONTEXT_CLASS = "com.github.nmorel.gwtjackson.client.JsonDecodingContext";
    protected static final String JSON_ENCODING_CONTEXT_CLASS = "com.github.nmorel.gwtjackson.client.JsonEncodingContext";
    protected static final String JSON_DECODING_EXCEPTION_CLASS = "com.github.nmorel.gwtjackson.client.exception.JsonDecodingException";
    protected static final String JSON_ENCODING_EXCEPTION_CLASS = "com.github.nmorel.gwtjackson.client.exception.JsonEncodingException";

    /**
     * Returns the String represention of the java type for a primitive for example int/Integer, float/Float, char/Character.
     *
     * @param type primitive type
     * @return the string representation
     */
    protected static String getJavaObjectTypeFor( JPrimitiveType type )
    {
        if ( type == JPrimitiveType.INT )
        {
            return "Integer";
        }
        else if ( type == JPrimitiveType.CHAR )
        {
            return "Character";
        }
        else
        {
            String s = type.getSimpleSourceName();
            return s.substring( 0, 1 ).toUpperCase() + s.substring( 1 );
        }
    }

    protected final TreeLogger logger;
    protected final GeneratorContext context;
    protected final JacksonTypeOracle typeOracle;

    protected AbstractJsonMapperCreator( TreeLogger logger, GeneratorContext context )
    {
        this( logger, context, new JacksonTypeOracle( logger, context.getTypeOracle() ) );
    }

    protected AbstractJsonMapperCreator( TreeLogger logger, GeneratorContext context, JacksonTypeOracle typeOracle )
    {
        this.logger = logger;
        this.context = context;
        this.typeOracle = typeOracle;
    }

    protected SourceWriter getSourceWriter( String packageName, String className, String superClass, String... interfaces )
    {
        ClassSourceFileComposerFactory composer = new ClassSourceFileComposerFactory( packageName, className );
        if ( null != superClass )
        {
            composer.setSuperclass( superClass );
        }
        for ( String interfaceName : interfaces )
        {
            composer.addImplementedInterface( interfaceName );
        }

        PrintWriter printWriter = context.tryCreate( logger, packageName, className );
        if ( printWriter == null )
        {
            return null;
        }
        else
        {
            return composer.createSourceWriter( context, printWriter );
        }
    }

    protected String createMapperFromType( JType type ) throws UnableToCompleteException
    {
        JPrimitiveType primitiveType = type.isPrimitive();
        if ( null != primitiveType )
        {
            String boxedName = getJavaObjectTypeFor( primitiveType );
            return "ctx.get" + boxedName + "JsonMapper()";
        }

        JEnumType enumType = type.isEnum();
        if ( null != enumType )
        {
            return "ctx.createEnumJsonMapper(" + enumType.getQualifiedSourceName() + ".class)";
        }

        JParameterizedType parameterizedType = type.isParameterized();
        if ( null != parameterizedType )
        {
            String result;

            if ( typeOracle.isSet( parameterizedType ) )
            {
                result = "ctx.createSetJsonMapper(%s)";
            }
            else if ( typeOracle.isList( parameterizedType ) )
            {
                result = "ctx.createListJsonMapper(%s)";
            }
            else if ( typeOracle.isCollection( parameterizedType ) )
            {
                result = "ctx.createCollectionJsonMapper(%s)";
            }
            else if ( typeOracle.isIterable( parameterizedType ) )
            {
                result = "ctx.createIterableJsonMapper(%s)";
            }
            else
            {
                // TODO
                logger.log( TreeLogger.Type.ERROR, "Parameterized type '" + parameterizedType
                    .getQualifiedSourceName() + "' is not supported" );
                throw new UnableToCompleteException();
            }

            JClassType[] args = parameterizedType.getTypeArgs();
            String[] mappers = new String[args.length];
            for ( int i = 0; i < args.length; i++ )
            {
                mappers[i] = createMapperFromType( args[i] );
            }

            return String.format( result, mappers );
        }

        JClassType classType = type.isClass();
        if ( null != classType )
        {
            String qualifiedSourceName = classType.getQualifiedSourceName();
            if ( BASE_TYPES.contains( qualifiedSourceName ) )
            {
                if ( qualifiedSourceName.startsWith( "java.sql" ) )
                {
                    return "ctx.getSql" + classType.getSimpleSourceName() + "JsonMapper()";
                }
                else
                {
                    return "ctx.get" + classType.getSimpleSourceName() + "JsonMapper()";
                }
            }

            // it's a bean, we create the mapper
            BeanJsonMapperCreator beanJsonMapperCreator = new BeanJsonMapperCreator( logger
                .branch( TreeLogger.Type.INFO, "Creating mapper for " + classType.getQualifiedSourceName() ), context, typeOracle );
            String name = beanJsonMapperCreator.create( classType );
            return "new " + name + "()";
        }

        logger.log( TreeLogger.Type.ERROR, "Type '" + type.getQualifiedSourceName() + "' is not supported" );
        throw new UnableToCompleteException();
    }
}