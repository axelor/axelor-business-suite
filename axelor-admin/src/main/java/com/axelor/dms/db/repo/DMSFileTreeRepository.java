package com.axelor.dms.db.repo;

import com.axelor.db.EntityHelper;
import com.axelor.db.Model;
import com.axelor.dms.db.DMSFile;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

public class DMSFileTreeRepository extends DMSFileRepository {

  /**
   * Gets root parent folder
   *
   * @param related model
   * @return root parent folder
   */
  @Override
  @Nullable
  protected DMSFile getRootParent(Model related) {
    DMSFile dmsRootParent = null;

    final Optional<Model> parentRelatedOpt =
        Arrays.stream(related.getClass().getDeclaredFields())
            .filter(
                field ->
                    field.isAnnotationPresent(ManyToOne.class)
                        && field.getType() != related.getClass()
                        && Arrays.stream(field.getType().getDeclaredFields())
                            .anyMatch(
                                parentField -> {
                                  final OneToMany oneToMany =
                                      parentField.getAnnotation(OneToMany.class);
                                  return oneToMany != null
                                      && field.getName().equals(oneToMany.mappedBy())
                                      && getFieldGenericType(parentField, 0) == related.getClass();
                                }))
            .map(field -> (Model) getPropertyOrNull(related, field.getName()))
            .filter(Objects::nonNull)
            .findFirst();

    if (parentRelatedOpt.isPresent()) {
      final Model parentRelated = parentRelatedOpt.get();
      dmsRootParent = findOrCreateHome(parentRelated);
    }

    return dmsRootParent;
  }

  /**
   * Gets the value of the specified property of the specified bean. Returns null if any error
   * occurred.
   *
   * @param bean
   * @param name
   * @return
   */
  @Nullable
  protected Object getPropertyOrNull(Model bean, String name) {
    try {
      return getProperty(bean, name);
    } catch (IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException
        | IntrospectionException e) {
      return null;
    }
  }

  /**
   * Gets the value of the specified property of the specified bean.
   *
   * @param bean
   * @param name
   * @return property value
   * @throws IntrospectionException
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   * @throws NoSuchMethodException
   */
  protected Object getProperty(Model bean, String name)
      throws IntrospectionException, IllegalAccessException, InvocationTargetException,
          NoSuchMethodException {
    final BeanInfo info = Introspector.getBeanInfo(bean.getClass());
    final Optional<PropertyDescriptor> pdOpt =
        Arrays.stream(info.getPropertyDescriptors())
            .filter(pd -> pd.getName().equals(name))
            .findFirst();

    if (pdOpt.isPresent()) {
      return EntityHelper.getEntity(pdOpt.get().getReadMethod().invoke(bean));
    }

    throw new NoSuchMethodException(name);
  }

  /**
   * Gets generic type of the specified field at the specified index.
   *
   * @param field
   * @param index
   * @return field generic class
   */
  protected Class<?> getFieldGenericType(Field field, int index) {
    Type type = field.getGenericType();

    if (type instanceof ParameterizedType) {
      final ParameterizedType ptype = (ParameterizedType) type;
      type = ptype.getActualTypeArguments()[index];

      if (type instanceof ParameterizedType) {
        return (Class<?>) ((ParameterizedType) type).getRawType();
      }
    }

    return (Class<?>) type;
  }
}
