export const queryKeys = {
  templates: ["public", "templates"] as const,
  templatesByCategory: (category: string) => ["public", "templates", category] as const,
  template: (id: string) => ["public", "template", id] as const,
  demoUsers: ["public", "demo-users"] as const,
  session: ["private", "session"] as const,
  userTasks: (userId: string) => ["private", userId, "tasks"] as const,
  userTask: (userId: string, taskId: string) => ["private", userId, "task", taskId] as const,
};

export function isPrivateKey(key: readonly unknown[]): boolean {
  return key[0] === "private";
}
