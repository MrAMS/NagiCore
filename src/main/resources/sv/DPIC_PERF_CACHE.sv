`include "DPIC_TYPES_DEFINE.sv"
import "DPI-C" function void dpic_perf_cache(input `uint8_t id, input `uint8_t access_type);
module DPIC_PERF_CACHE #(
    parameter DATA_WIDTH = 32
) (
    input   wire clk,
    input   wire rst,
    input   wire valid,
    input   wire [7:0] id,
    input   wire [7:0] access_type
    
);

always @(posedge clk) begin
    if(!rst && valid) begin
        dpic_perf_cache(id, access_type);
    end
end
    
endmodule