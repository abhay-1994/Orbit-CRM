import type { FormRule } from 'antd'

/** "Confirm password" must equal the `password` field of the same form. */
export const confirmRule: FormRule = ({ getFieldValue }) => ({
  validator: (_, value) =>
    !value || value === getFieldValue('password') ? Promise.resolve() : Promise.reject(new Error('The passwords do not match')),
})
