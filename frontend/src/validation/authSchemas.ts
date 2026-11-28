import { z } from 'zod'

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/
/** Indian mobile: 10 digits starting 6-9, optionally with +91 / 91 / 0 in front and spaces or dashes. */
const MOBILE = /^(?:\+?91|0)?[6-9]\d{9}$/

export const loginSchema = z.object({
  identifier: z
    .string()
    .trim()
    .min(1, 'Enter your email or mobile number')
    .max(160, 'Use 160 characters or fewer')
    .refine((v) => (v.includes('@') ? EMAIL.test(v) : MOBILE.test(v.replace(/[\s-]/g, ''))), 'Enter a valid email or 10-digit mobile number'),
  password: z.string().min(1, 'Enter your password').max(200, 'Use 200 characters or fewer'),
  remember: z.boolean(),
})

export type LoginForm = z.infer<typeof loginSchema>
