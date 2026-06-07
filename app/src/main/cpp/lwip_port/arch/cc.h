#ifndef LWIP_CUSTOM_CC_H
#define LWIP_CUSTOM_CC_H
#include <stdint.h>
#include <stddef.h>

#define U16_F "hu"
#define S16_F "hd"
#define X16_F "hx"
#define U32_F "u"
#define S32_F "d"
#define X32_F "x"

#define PACK_STRUCT_FIELD(x) x
#define PACK_STRUCT_STRUCT __attribute__((packed))
#define PACK_STRUCT_BEGIN
#define PACK_STRUCT_END

#define LWIP_PLATFORM_DIAG(x)
#define LWIP_PLATFORM_ASSERT(x)

typedef int sys_prot_t;

#endif /* LWIP_CUSTOM_CC_H */
