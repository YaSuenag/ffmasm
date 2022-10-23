CC = gcc
AS = as
ASFLAGS = -g -c --noexecstack
LDFLAGS  = -Wl,-z,noexecstack

TARGET = librdtsc.so
OBJS   = target/rdtsc.o
SRCS   = src/main/native/rdtsc.S

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) -shared -o $@ $^ $(LDFLAGS)
	mv $(TARGET) target/$(TARGET)

$(OBJS): $(SRCS)
	$(AS) $(ASFLAGS) $< -o $@
