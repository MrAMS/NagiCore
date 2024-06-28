`include "DPIC_TYPES_DEFINE.sv"
import "DPI-C" function void dpic_trace_mem(input `uint32_t addr, input `uint8_t size, input `uint32_t data, input `uint8_t wmask);
module DPIC_TRACE_MEM #(
    parameter ADDR_WIDTH = 32,
    parameter DATA_WIDTH = 32
) (
    input   wire clk,
    input   wire rst,
    input   wire valid,
    input   wire [ADDR_WIDTH-1:0] addr,
    input   wire [DATA_WIDTH/8-1:0] wmask,
    input   wire [1:0] size,
    input   wire [ADDR_WIDTH-1:0] data
);

always @(posedge clk) begin
    if(!rst && valid) begin
        dpic_trace_mem({{32-ADDR_WIDTH{1'b0}}, addr}, {6'b0, size}, {{32-DATA_WIDTH{1'b0}}, data}, {{8-DATA_WIDTH/8{1'b0}}, wmask});
    end
end
    
endmodule