import React from 'react';

export interface FormTemplateProps {
  children: React.ReactNode;
  onSubmit?: (e: React.FormEvent<HTMLFormElement>) => void;
  className?: string;
  sectionClassName?: string;
}

/**
 * Light-weight form wrapper. Mirrors mvt-v2's FormTemplate role (section >
 * form) but doesn't pull in AntD <Form /> because pages drive their fields
 * imperatively through controlled CustomInput / CustomTextarea / CustomSelect.
 */
const FormTemplate: React.FC<FormTemplateProps> = ({
  children,
  onSubmit,
  className,
  sectionClassName,
}) => (
  <section className={sectionClassName}>
    <form
      className={className}
      onSubmit={(e) => {
        if (onSubmit) {
          onSubmit(e);
        } else {
          e.preventDefault();
        }
      }}
    >
      {children}
    </form>
  </section>
);

export default FormTemplate;
