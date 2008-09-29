/*
 * Copyright 2007 The authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.struts2;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.struts2.dom.Param;
import com.intellij.struts2.dom.inspection.Struts2ModelInspection;
import com.intellij.struts2.dom.inspection.ValidatorConfigModelInspection;
import com.intellij.struts2.dom.inspection.ValidatorModelInspection;
import com.intellij.struts2.dom.struts.Bean;
import com.intellij.struts2.dom.struts.Constant;
import com.intellij.struts2.dom.struts.Include;
import com.intellij.struts2.dom.struts.StrutsRoot;
import com.intellij.struts2.dom.struts.action.Action;
import com.intellij.struts2.dom.struts.action.ExceptionMapping;
import com.intellij.struts2.dom.struts.action.Result;
import com.intellij.struts2.dom.struts.strutspackage.*;
import com.intellij.struts2.dom.validator.Field;
import com.intellij.struts2.dom.validator.FieldValidator;
import com.intellij.struts2.dom.validator.Message;
import com.intellij.struts2.dom.validator.Validators;
import com.intellij.struts2.dom.validator.config.ValidatorConfig;
import com.intellij.struts2.facet.StrutsFacetType;
import com.intellij.util.Icons;
import com.intellij.util.NullableFunction;
import com.intellij.util.ReflectionCache;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.TypeNameManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Application-level support.
 * <p/>
 * <ul>
 * <li>StrutsFacet</li>
 * <li>External resources (DTDs)</li>
 * <li>DOM Icons/presentation</li>
 * <li>Inspections</li>
 * </ul>
 *
 * @author Yann C&eacute;bron
 */
public class StrutsApplicationComponent implements ApplicationComponent,
                                                   FileTemplateGroupDescriptorFactory,
                                                   InspectionToolProvider {

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Struts2ApplicationComponent";
  }

  public void initComponent() {
    FacetTypeRegistry.getInstance().registerFacetType(StrutsFacetType.INSTANCE);

    initExternalResources();

    registerStrutsDomPresentation();
    registerValidationDomPresentation();
  }

  public void disposeComponent() {
  }

  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    final FileTemplateGroupDescriptor group = new FileTemplateGroupDescriptor(StrutsBundle.message("struts2"),
                                                                              StrutsIcons.ACTION);
    group.addTemplate(new FileTemplateDescriptor(StrutsConstants.STRUTS_DEFAULT_FILENAME,
                                                 StrutsIcons.STRUTS_CONFIG_FILE_ICON));
    group.addTemplate(new FileTemplateDescriptor("validator.xml", StrutsIcons.VALIDATION_CONFIG_FILE_ICON));
    return group;
  }

  public Class[] getInspectionClasses() {
    return new Class[]{Struts2ModelInspection.class, ValidatorModelInspection.class, ValidatorConfigModelInspection.class};
  }

  /**
   * Provides display name for subclass(es) of given DomElement-type.
   *
   * @param <T> DomElement-type to provide names for.
   */
  private abstract static class TypedNameProvider<T extends DomElement> implements NullableFunction<Object, String> {

    private final Class clazz;

    private TypedNameProvider(final Class<T> clazz) {
      this.clazz = clazz;
    }

    @Nullable
    public String fun(final Object o) {
      if (ReflectionCache.isInstance(o, clazz)) {
        //noinspection unchecked
        return getDisplayName((T) o);
      }

      return null;
    }

    @Nullable
    protected abstract String getDisplayName(T t);

  }


  private static void registerStrutsDomPresentation() {
    // <struts>
    ElementPresentationManager.registerIcon(StrutsRoot.class, StrutsIcons.STRUTS_CONFIG_FILE_ICON);
    ElementPresentationManager.registerNameProvider(new TypedNameProvider<StrutsRoot>(StrutsRoot.class) {
      protected String getDisplayName(final StrutsRoot strutsRoot) {
        return strutsRoot.getRoot().getFile().getName();
      }
    });

    // <exception-mapping>
    ElementPresentationManager.registerIcon(ExceptionMapping.class, StrutsIcons.EXCEPTION_MAPPING);
    ElementPresentationManager.registerNameProvider(new TypedNameProvider<ExceptionMapping>(ExceptionMapping.class) {
      protected String getDisplayName(final ExceptionMapping exceptionMapping) {
        final PsiClass exceptionClass = exceptionMapping.getExceptionClass().getValue();
        if (exceptionClass != null) {
          return exceptionClass.getName();
        }
        return exceptionMapping.getName().getStringValue();
      }
    });

    // global <exception-mapping>
    ElementPresentationManager.registerIcon(GlobalExceptionMapping.class, StrutsIcons.GLOBAL_EXCEPTION_MAPPING);
    ElementPresentationManager.registerNameProvider(new TypedNameProvider<GlobalExceptionMapping>(GlobalExceptionMapping.class) {
      protected String getDisplayName(final GlobalExceptionMapping globalExceptionMapping) {
        final PsiClass exceptionClass = globalExceptionMapping.getExceptionClass().getValue();
        if (exceptionClass != null) {
          return exceptionClass.getName();
        }
        return globalExceptionMapping.getName().getStringValue();
      }
    });

    // <interceptor-ref>
    ElementPresentationManager.registerIconProvider(new NullableFunction<Object, Icon>() {
      public Icon fun(final Object o) {
        if (o instanceof InterceptorRef) {
          final InterceptorOrStackBase interceptorOrStackBase = ((InterceptorRef) o).getName().getValue();
          if (interceptorOrStackBase instanceof Interceptor) {
            return StrutsIcons.INTERCEPTOR;
          } else if (interceptorOrStackBase instanceof InterceptorStack) {
            return StrutsIcons.INTERCEPTOR_STACK;
          }
        }
        return null;
      }
    });

    ElementPresentationManager.registerNameProvider(new TypedNameProvider<InterceptorRef>(InterceptorRef.class) {
      protected String getDisplayName(final InterceptorRef interceptorRef) {
        return interceptorRef.getName().getStringValue();
      }
    });

    ElementPresentationManager.registerIcon(DefaultInterceptorRef.class, StrutsIcons.DEFAULT_INTERCEPTOR_REF);
    ElementPresentationManager.registerNameProvider(new TypedNameProvider<DefaultInterceptorRef>(DefaultInterceptorRef.class) {
      protected String getDisplayName(final DefaultInterceptorRef defaultInterceptorRef) {
        return defaultInterceptorRef.getName().getStringValue();
      }
    });

    // <include>
    ElementPresentationManager.registerIcon(Include.class, StrutsIcons.INCLUDE);
    ElementPresentationManager.registerNameProvider(new TypedNameProvider<Include>(Include.class) {
      protected String getDisplayName(final Include include) {
        return include.getFile().getStringValue();
      }
    });

    // <result>
    ElementPresentationManager.registerIcon(Result.class, StrutsIcons.RESULT);
    ElementPresentationManager.registerNameProvider(new TypedNameProvider<Result>(Result.class) {
      protected String getDisplayName(final Result result) {
        final String resultName = result.getName().getStringValue();
        return resultName != null ? resultName : Result.DEFAULT_NAME;
      }
    });

    // <global-result>
    ElementPresentationManager.registerIcon(GlobalResult.class, StrutsIcons.GLOBAL_RESULT);
    TypeNameManager.registerTypeName(GlobalResult.class, "global result");
    ElementPresentationManager.registerNameProvider(new TypedNameProvider<GlobalResult>(GlobalResult.class) {
      protected String getDisplayName(final GlobalResult globalResult) {
        final String globalResultName = globalResult.getName().getStringValue();
        return globalResultName != null ? globalResultName : Result.DEFAULT_NAME;
      }
    });

    // <default-action-ref>
    ElementPresentationManager.registerIcon(DefaultActionRef.class, StrutsIcons.DEFAULT_ACTION_REF);
    ElementPresentationManager.registerNameProvider(new TypedNameProvider<DefaultActionRef>(DefaultActionRef.class) {
      protected String getDisplayName(final DefaultActionRef defaultActionRef) {
        return defaultActionRef.getName().getStringValue();
      }
    });

    // <default-class-ref>
    ElementPresentationManager.registerIcon(DefaultClassRef.class, StrutsIcons.DEFAULT_CLASS_REF);
    ElementPresentationManager.registerNameProvider(new TypedNameProvider<DefaultClassRef>(DefaultClassRef.class) {
      protected String getDisplayName(final DefaultClassRef defaultClassRef) {
        return defaultClassRef.getDefaultClass().getStringValue();
      }
    });

    ElementPresentationManager.registerIcon(Action.class, StrutsIcons.ACTION);
    ElementPresentationManager.registerIcon(Bean.class, StrutsIcons.BEAN);
    ElementPresentationManager.registerIcon(Constant.class, Icons.PARAMETER_ICON);
    ElementPresentationManager.registerIcon(Interceptor.class, StrutsIcons.INTERCEPTOR);
    ElementPresentationManager.registerIcon(InterceptorStack.class, StrutsIcons.INTERCEPTOR_STACK);
    ElementPresentationManager.registerIcon(Param.class, StrutsIcons.PARAM);
    ElementPresentationManager.registerIcon(ResultType.class, StrutsIcons.RESULT_TYPE);
    ElementPresentationManager.registerIcon(StrutsPackage.class, StrutsIcons.PACKAGE);
  }

  private static void registerValidationDomPresentation() {
    ElementPresentationManager.registerIcon(ValidatorConfig.class, StrutsIcons.VALIDATOR);

    ElementPresentationManager.registerIcon(Validators.class, StrutsIcons.VALIDATION_CONFIG_FILE_ICON);
    ElementPresentationManager.registerNameProvider(new TypedNameProvider<Validators>(Validators.class) {
      protected String getDisplayName(final Validators validators) {
        return validators.getRoot().getFile().getName();
      }
    });

    // <field>
    ElementPresentationManager.registerIcon(Field.class, Icons.FIELD_ICON);
    ElementPresentationManager.registerNameProvider(new TypedNameProvider<Field>(Field.class) {
      protected String getDisplayName(final Field field) {
        return field.getName().getStringValue();
      }
    });

    // <field-validator>
    ElementPresentationManager.registerIcon(FieldValidator.class, StrutsIcons.VALIDATOR);
    ElementPresentationManager.registerNameProvider(new TypedNameProvider<FieldValidator>(FieldValidator.class) {
      protected String getDisplayName(final FieldValidator fieldValidator) {
        final ValidatorConfig validatorConfig = fieldValidator.getType().getValue();
        return validatorConfig != null ? validatorConfig.getName().getStringValue() : null;
      }
    });

    // <message>
    ElementPresentationManager.registerIcon(Message.class, StrutsIcons.MESSAGE);
    ElementPresentationManager.registerNameProvider(new TypedNameProvider<Message>(Message.class) {
      protected String getDisplayName(final Message message) {
        final String key = message.getKey().getStringValue();
        return StringUtil.isNotEmpty(key) ? key : message.getValue();
      }
    });
  }

  /**
   * Adds all Struts2-related DTDs to the available external resources.
   */
  private static void initExternalResources() {
    addDTDResource(StrutsConstants.STRUTS_2_0_DTD_URI,
                   StrutsConstants.STRUTS_2_0_DTD_ID,
                   "/resources/dtds/struts-2.0.dtd");

    addDTDResource(StrutsConstants.XWORK_DTD_URI,
                   StrutsConstants.XWORK_DTD_ID,
                   "/resources/dtds/xwork-2.0.dtd");

    addDTDResource(StrutsConstants.VALIDATOR_1_00_DTD_URI,
                   StrutsConstants.VALIDATOR_1_00_DTD_ID,
                   "/resources/dtds/xwork-validator-1.0.dtd");

    addDTDResource(StrutsConstants.VALIDATOR_1_02_DTD_URI,
                   StrutsConstants.VALIDATOR_1_02_DTD_ID,
                   "/resources/dtds/xwork-validator-1.0.2.dtd");

    addDTDResource(StrutsConstants.VALIDATOR_CONFIG_DTD_URI,
                   StrutsConstants.VALIDATOR_CONFIG_DTD_ID,
                   "/resources/dtds/xwork-validator-config-1.0.dtd");

    addDTDResource(StrutsConstants.TILES_2_DTD_URI_STRUTS,
                   StrutsConstants.TILES_2_DTD_ID,
                   "/resources/dtds/struts-tiles-config_2_0.dtd");

    addDTDResource(StrutsConstants.TILES_2_DTD_URI,
                   StrutsConstants.TILES_2_DTD_ID,
                   "/resources/dtds/tiles-config_2_0.dtd");
  }

  /**
   * Adds a DTD resource from local classpath.
   *
   * @param uri       Resource URI.
   * @param id        Resource ID.
   * @param localFile Local path to resource.
   */
  private static void addDTDResource(@NonNls final String uri,
                                     @NonNls final String id,
                                     @NonNls final String localFile) {
    ExternalResourceManager.getInstance().addStdResource(uri, localFile, StrutsApplicationComponent.class);
    ExternalResourceManager.getInstance().addStdResource(id, localFile, StrutsApplicationComponent.class);
  }

}