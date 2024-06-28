BUILD_DIR 	= build
SRC 		= $(shell find src/main/scala -name "*.scala")
SRC			+= $(shell find src/main/resources/sv -name "*.sv" -or -name "*.v")

TARGET 		= $(BUILD_DIR)/Core.v

$(TARGET): $(SRC)
	-rm -rf $(BUILD_DIR)
	mill nagicore.run hello

generate: $(TARGET)

test: generate
	xmake b diff
	xmake r diff

wave:
	xmake r wave

config:
	xmake f --menu

clean: 
	-rm -rf $(BUILD_DIR)

intellij-init:
	mill mill.idea.GenIdea/idea

.PHONY: intellij-init clean config wave