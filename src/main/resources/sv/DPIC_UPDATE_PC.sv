`include "DPIC_TYPES_DEFINE.sv"
import "DPI-C" function void dpic_update_pc(input `uint32_t value);
module DPIC_UPDATE_PC #(
    parameter DATA_WIDTH = 32
) (
    input   wire clk,
    input   wire rst,
    input   wire wen,
    input   wire [DATA_WIDTH-1:0] pc
);

always @(posedge clk) begin
    if(!rst && wen) begin
        dpic_update_pc(pc);
    end
end
    
endmodule