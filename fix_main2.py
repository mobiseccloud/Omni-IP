with open('app/src/main/java/com/mobisec/omniip/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix BillingManager
content = content.replace('billingManager = BillingManager(this)', 'billingManager = BillingManager(this, lifecycleScope)')

# Fix checkEnvironment
check_env_old = '''isRaspCompromised = NativeEngine.checkEnvironment()'''
check_env_new = '''try {
    NativeEngine.executeSecuritySweep(this@MainActivity)
} catch (e: Exception) {
    isRaspCompromised = true
}'''
content = content.replace(check_env_old, check_env_new)

with open('app/src/main/java/com/mobisec/omniip/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(content)
