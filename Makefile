BUILD_DIR 	= build
SRC 		= $(shell find src/main/scala -name "*.scala")
SRC			+= $(shell find src/main/resources/sv -name "*.sv" -or -name "*.v")

TARGET 		= $(BUILD_DIR)/Core.v

$(TARGET): $(SRC)
	-rm -rf build
	mill nagicore.run hello

generate: $(TARGET)

intellij-init:
	mill mill.idea.GenIdea/idea

.PHONY: intellij-init