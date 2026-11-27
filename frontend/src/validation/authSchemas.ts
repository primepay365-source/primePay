import { z } from 'zod'

export const loginSchema = z.object({
  username: z.string().trim().min(1, 'Enter your username or email').max(80, 'Use 80 characters or fewer'),
  password: z.string().min(1, 'Enter your password').max(200, 'Use 200 characters or fewer'),
  remember: z.boolean(),
})

export type LoginForm = z.infer<typeof loginSchema>
